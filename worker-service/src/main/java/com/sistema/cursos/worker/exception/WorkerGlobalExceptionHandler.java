package com.sistema.cursos.worker.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejador global de excepciones del Worker Service.
 *
 * <p>Intercepta excepciones lanzadas por los controladores REST y las
 * convierte a respuestas JSON homogéneas con el código HTTP apropiado.
 *
 * <p>Excepciones manejadas:
 * <ul>
 *   <li>{@link EntityNotFoundException}: ID inexistente en Oracle Cloud → HTTP 404.</li>
 *   <li>{@link NoSuchKeyException}: clave S3 no encontrada al descargar PDF → HTTP 404.</li>
 *   <li>{@link S3Exception}: error genérico de AWS S3 → HTTP 502.</li>
 *   <li>{@link IllegalArgumentException}: datos inválidos en la solicitud → HTTP 400.</li>
 *   <li>{@link Exception}: cualquier error no controlado → HTTP 500.</li>
 * </ul>
 */
@RestControllerAdvice
public class WorkerGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkerGlobalExceptionHandler.class);

    // ── EntityNotFoundException (HTTP 404) ────────────────────────────────

    /**
     * Captura el caso en que se busca, actualiza o elimina una inscripción
     * con un ID que no existe en la base de datos Oracle Cloud.
     *
     * @param ex excepción lanzada por {@code InscripcionServiceImpl}
     * @return HTTP 404 Not Found con mensaje descriptivo en JSON
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(
            EntityNotFoundException ex) {

        log.warn("Recurso no encontrado: {}", ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(construirCuerpo(
                HttpStatus.NOT_FOUND,
                "Recurso no encontrado",
                ex.getMessage()
            ));
    }

    // ── NoSuchKeyException S3 (HTTP 404) ──────────────────────────────────

    /**
     * Captura el caso en que el PDF referenciado no existe en el bucket S3.
     * Ocurre si se solicita descargar un comprobante cuya clave S3 fue eliminada.
     *
     * @param ex excepción del AWS SDK v2 para clave inexistente
     * @return HTTP 404 Not Found
     */
    @ExceptionHandler(NoSuchKeyException.class)
    public ResponseEntity<Map<String, Object>> handleS3KeyNotFound(
            NoSuchKeyException ex) {

        log.warn("Archivo PDF no encontrado en S3: {}", ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(construirCuerpo(
                HttpStatus.NOT_FOUND,
                "Comprobante PDF no encontrado en el almacenamiento S3",
                "El archivo solicitado no existe o fue eliminado del bucket."
            ));
    }

    // ── S3Exception genérica (HTTP 502) ───────────────────────────────────

    /**
     * Captura errores genéricos de AWS S3 (permisos, bucket inexistente, etc.)
     * que pueden ocurrir en el endpoint de descarga.
     *
     * @param ex excepción del AWS SDK v2
     * @return HTTP 502 Bad Gateway
     */
    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<Map<String, Object>> handleS3Exception(S3Exception ex) {

        log.error("Error de AWS S3 — código: {} — mensaje: {}", ex.statusCode(), ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(construirCuerpo(
                HttpStatus.BAD_GATEWAY,
                "Error al acceder al almacenamiento en la nube",
                "No fue posible completar la operación con AWS S3. Intente más tarde."
            ));
    }

    // ── IllegalArgumentException (HTTP 400) ──────────────────────────────

    /**
     * Captura argumentos inválidos, como estados de inscripción no válidos
     * enviados en el cuerpo del PUT.
     *
     * @param ex excepción de argumento inválido
     * @return HTTP 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn("Argumento inválido en la solicitud: {}", ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(construirCuerpo(
                HttpStatus.BAD_REQUEST,
                "Solicitud inválida",
                ex.getMessage()
            ));
    }

    // ── Exception genérica (HTTP 500) ─────────────────────────────────────

    /**
     * Fallback para cualquier excepción no controlada explícitamente.
     *
     * @param ex excepción inesperada
     * @return HTTP 500 Internal Server Error con mensaje genérico
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        log.error("Error interno no controlado en Worker Service: {}", ex.getMessage(), ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(construirCuerpo(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor",
                "Ocurrió un error inesperado. Contacte al administrador del sistema."
            ));
    }

    // ── Método privado de construcción de respuesta ───────────────────────

    /**
     * Construye el mapa de respuesta de error con formato homogéneo.
     *
     * @param status  código HTTP
     * @param error   descripción corta del error
     * @param detalle mensaje explicativo para el cliente
     * @return mapa ordenado serializable como JSON
     */
    private Map<String, Object> construirCuerpo(
            HttpStatus status, String error, String detalle) {

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now().toString());
        cuerpo.put("status",    status.value());
        cuerpo.put("error",     error);
        cuerpo.put("detalle",   detalle);
        cuerpo.put("servicio",  "worker-service");
        return cuerpo;
    }
}
