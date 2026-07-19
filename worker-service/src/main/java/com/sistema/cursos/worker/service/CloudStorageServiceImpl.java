package com.sistema.cursos.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Implementación del servicio de almacenamiento en AWS S3.
 *
 * <p>Usa el {@link S3Client} del AWS SDK v2, configurado en
 * {@link com.sistema.cursos.worker.config.S3Config} con
 * {@code DefaultCredentialsProvider} (credenciales de AWS Academy por env vars).
 *
 * <p>El bucket y la región se inyectan desde {@code application.yml},
 * que a su vez los toma de las variables de entorno
 * {@code AWS_S3_BUCKET_NAME} y {@code AWS_S3_REGION}.
 *
 * <p>Estructura de claves S3 usada:
 * <pre>
 *   resumen-{inscripcionId}/
 *   └── comprobante.pdf
 * </pre>
 *
 * <p>Ejemplo: {@code resumen-42/comprobante.pdf}
 */
@Service
public class CloudStorageServiceImpl implements CloudStorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudStorageServiceImpl.class);

    /** Tipo de contenido del archivo subido a S3. */
    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private final S3Client s3Client;
    private final String   bucketName;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param s3Client   cliente S3 del AWS SDK v2, configurado en {@code S3Config}
     * @param bucketName nombre del bucket leído de {@code aws.s3.bucket-name}
     */
    public CloudStorageServiceImpl(
            S3Client s3Client,
            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.s3Client   = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sube el PDF al bucket S3 bajo la ruta {@code resumen-{id}/comprobante.pdf}.
     * Incluye el Content-Type {@code application/pdf} para que el navegador
     * pueda abrirlo directamente desde una URL presignada.
     *
     * @param contenido     bytes del PDF generado con OpenPDF
     * @param inscripcionId ID de la inscripción (define el nombre de la carpeta)
     * @return clave S3 del objeto subido (ej: {@code resumen-42/comprobante.pdf})
     * @throws S3Exception si ocurre un error de comunicación con AWS S3
     */
    @Override
    public String subirPdf(byte[] contenido, Long inscripcionId) {
        // Construir la clave S3: carpeta = número de resumen (inscripcionId)
        String claveS3 = "resumen-" + inscripcionId + "/comprobante.pdf";

        log.info("Subiendo comprobante PDF a S3 — bucket: [{}], clave: [{}], tamaño: {} bytes",
            bucketName, claveS3, contenido.length);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(claveS3)
                .contentType(CONTENT_TYPE_PDF)
                .contentLength((long) contenido.length)
                .build();

            PutObjectResponse response = s3Client.putObject(
                putRequest,
                RequestBody.fromBytes(contenido)
            );

            log.info("PDF subido exitosamente a S3 — clave: [{}], eTag: [{}]",
                claveS3, response.eTag());

            return claveS3;

        } catch (S3Exception ex) {
            log.error("Error al subir PDF a S3 — bucket: [{}], clave: [{}] — código: {} — mensaje: {}",
                bucketName, claveS3, ex.statusCode(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Descarga el contenido binario de un comprobante PDF desde S3.
     * Usado por el endpoint {@code GET /api/v1/inscripciones/{id}/descargar}
     * del Worker Service para retornar el PDF al cliente.
     *
     * @param claveS3 clave del objeto en S3
     * @return bytes del PDF almacenado
     * @throws S3Exception si la clave no existe o falla la descarga
     */
    @Override
    public byte[] descargarPdf(String claveS3) {
        log.info("Descargando PDF desde S3 — bucket: [{}], clave: [{}]",
            bucketName, claveS3);

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(claveS3)
                .build();

            ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObject(
                getRequest,
                ResponseTransformer.toBytes()
            );

            byte[] contenido = responseBytes.asByteArray();

            log.info("PDF descargado exitosamente — clave: [{}], tamaño: {} bytes",
                claveS3, contenido.length);

            return contenido;

        } catch (S3Exception ex) {
            log.error("Error al descargar PDF desde S3 — clave: [{}] — código: {} — mensaje: {}",
                claveS3, ex.statusCode(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Elimina el comprobante PDF del bucket S3.
     * Llamado desde el endpoint {@code DELETE /api/v1/inscripciones/{id}}
     * para limpiar el archivo almacenado cuando se borra una inscripción.
     *
     * @param claveS3 clave del objeto a eliminar
     */
    @Override
    public void eliminarPdf(String claveS3) {
        log.info("Eliminando PDF de S3 — bucket: [{}], clave: [{}]",
            bucketName, claveS3);

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(claveS3)
                .build();

            s3Client.deleteObject(deleteRequest);

            log.info("PDF eliminado exitosamente de S3 — clave: [{}]", claveS3);

        } catch (S3Exception ex) {
            // Loggar pero no relanzar: si S3 ya no tiene el archivo,
            // la inscripción en Oracle igual debe eliminarse.
            log.warn("No se pudo eliminar el PDF de S3 — clave: [{}] — código: {} — mensaje: {}",
                claveS3, ex.statusCode(), ex.getMessage());
        }
    }
}
