package com.sistema.cursos.worker.service;

/**
 * Contrato del servicio de almacenamiento en la nube (AWS S3).
 *
 * <p>Define las operaciones de gestión del comprobante PDF en el bucket S3.
 * Separar la interfaz de la implementación permite:
 * <ul>
 *   <li>Sustituir S3 por otro proveedor (GCS, Azure Blob) sin cambiar
 *       el {@code InscripcionService}.</li>
 *   <li>Mockear el servicio en pruebas unitarias sin conexión real a AWS.</li>
 * </ul>
 */
public interface CloudStorageService {

    /**
     * Sube el contenido de un comprobante PDF al bucket S3 configurado.
     *
     * <p>La clave S3 generada sigue la estructura:
     * {@code resumen-{inscripcionId}/comprobante.pdf}
     *
     * @param contenido     bytes del PDF generado por OpenPDF
     * @param inscripcionId identificador de la inscripción (usado para la ruta S3)
     * @return la clave S3 del objeto subido (ej: {@code resumen-42/comprobante.pdf})
     * @throws software.amazon.awssdk.services.s3.model.S3Exception si la subida falla
     */
    String subirPdf(byte[] contenido, Long inscripcionId);

    /**
     * Descarga el contenido binario de un comprobante PDF desde S3.
     *
     * @param claveS3 clave del objeto en S3 (obtenida de {@code Inscripcion.getArchivoS3Key()})
     * @return bytes del archivo PDF almacenado
     * @throws software.amazon.awssdk.services.s3.model.NoSuchKeyException si la clave no existe
     */
    byte[] descargarPdf(String claveS3);

    /**
     * Elimina el comprobante PDF asociado a una inscripción del bucket S3.
     *
     * @param claveS3 clave del objeto a eliminar
     */
    void eliminarPdf(String claveS3);
}
