package com.sistema.cursos.bff.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones del BFF Service.
 *
 * <p>Centraliza el manejo de errores siguiendo el principio de
 * responsabilidad única (SRP): ningún controlador necesita manejar
 * excepciones propias, este componente se encarga de todas.
 *
 * <p>Excepciones manejadas:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException}: validación de Bean Validation fallida → HTTP 400.</li>
 *   <li>{@link AmqpConnectException}: broker RabbitMQ no disponible → HTTP 503.</li>
 *   <li>{@link AmqpException}: error genérico de mensajería → HTTP 503.</li>
 *   <li>{@link Exception}: cualquier error no controlado → HTTP 500.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Validación de campos (HTTP 400) ───────────────────────────────────

    /**
     * Captura errores de validación de Bean Validation en el payload de entrada.
     * Devuelve un cuerpo JSON con la lista de campos inválidos y sus mensajes.
     *
     * @param ex excepción lanzada por {@code @Valid} cuando el payload no cumple las restricciones
     * @return HTTP 400 Bad Request con mapa de errores de validación
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        // Extraer todos los errores de campo con sus mensajes personalizados
        List<Map<String, String>> erroresCampos = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::construirErrorCampo)
            .collect(Collectors.toList());

        log.warn("Solicitud rechazada por validación — {} error(es) de campo: {}",
            erroresCampos.size(), erroresCampos);

        Map<String, Object> cuerpo = construirCuerpoError(
            HttpStatus.BAD_REQUEST,
            "Validación fallida: los datos de la solicitud son incorrectos o incompletos.",
            erroresCampos
        );

        return ResponseEntity.badRequest().body(cuerpo);
    }

    // ── Errores de conectividad con RabbitMQ (HTTP 503) ───────────────────

    /**
     * Captura errores de conexión con RabbitMQ.
     * Se activa cuando el broker no está disponible en el momento de publicar.
     *
     * @param ex excepción de conexión lanzada por {@link org.springframework.amqp.rabbit.core.RabbitTemplate}
     * @return HTTP 503 Service Unavailable
     */
    @ExceptionHandler(AmqpConnectException.class)
    public ResponseEntity<Map<String, Object>> handleAmqpConnectException(
            AmqpConnectException ex) {

        log.error("Broker RabbitMQ no disponible: {}", ex.getMessage(), ex);

        Map<String, Object> cuerpo = construirCuerpoError(
            HttpStatus.SERVICE_UNAVAILABLE,
            "El servicio de mensajería no está disponible. Intente nuevamente más tarde.",
            null
        );

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(cuerpo);
    }

    /**
     * Captura cualquier error genérico de AMQP (publicación fallida, timeout, etc.).
     *
     * @param ex excepción genérica de AMQP
     * @return HTTP 503 Service Unavailable
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<Map<String, Object>> handleAmqpException(AmqpException ex) {

        log.error("Error en la comunicación con RabbitMQ: {}", ex.getMessage(), ex);

        Map<String, Object> cuerpo = construirCuerpoError(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Error al procesar la solicitud. El sistema de colas no pudo recibir el mensaje.",
            null
        );

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(cuerpo);
    }

    // ── Error genérico no controlado (HTTP 500) ───────────────────────────

    /**
     * Fallback para cualquier excepción no controlada explícitamente.
     * Evita exponer detalles internos del sistema al cliente.
     *
     * @param ex excepción inesperada
     * @return HTTP 500 Internal Server Error con mensaje genérico
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        log.error("Error interno no controlado: {}", ex.getMessage(), ex);

        Map<String, Object> cuerpo = construirCuerpoError(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Ocurrió un error interno. Por favor contacte al administrador del sistema.",
            null
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(cuerpo);
    }

    // ── Métodos privados de construcción de respuesta ─────────────────────

    /**
     * Construye el mapa con los detalles de un error de campo individual.
     *
     * @param fieldError error de campo de Bean Validation
     * @return mapa con {@code campo} y {@code mensaje}
     */
    private Map<String, String> construirErrorCampo(FieldError fieldError) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("campo", fieldError.getField());
        error.put("valorRecibido", fieldError.getRejectedValue() != null
            ? fieldError.getRejectedValue().toString() : "null");
        error.put("mensaje", fieldError.getDefaultMessage());
        return error;
    }

    /**
     * Construye el cuerpo de respuesta de error con formato homogéneo.
     *
     * @param status   código HTTP de la respuesta
     * @param mensaje  descripción legible del error
     * @param detalles lista de errores adicionales (puede ser null)
     * @return mapa ordenado listo para serializar a JSON
     */
    private Map<String, Object> construirCuerpoError(
            HttpStatus status, String mensaje, Object detalles) {

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("error", status.getReasonPhrase());
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("servicio", "bff-service");

        if (detalles != null) {
            cuerpo.put("errores", detalles);
        }

        return cuerpo;
    }
}
