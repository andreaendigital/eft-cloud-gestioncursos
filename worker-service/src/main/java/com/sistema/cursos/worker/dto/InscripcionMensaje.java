package com.sistema.cursos.worker.dto;

import java.util.List;

/**
 * DTO que representa el mensaje consumido desde la cola {@code cola-inscripciones}.
 *
 * <p>Espejo exacto del {@code InscripcionRequest} publicado por el BFF Service.
 * El {@link com.sistema.cursos.worker.config.RabbitMQConfig} configura
 * {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter},
 * lo que permite que Spring AMQP deserialice automáticamente el JSON del
 * mensaje a esta clase cuando el {@code @RabbitListener} lo recibe.
 *
 * <p>Ejemplo del JSON que llega desde la cola:
 * <pre>{@code
 * {
 *   "estudianteId":     "1024",
 *   "nombreEstudiante": "Juan Pérez",
 *   "emailEstudiante":  "juan.perez@email.com",
 *   "cursosIds":        [5, 12, 43]
 * }
 * }</pre>
 *
 * <p>Uso en el listener:
 * <pre>{@code
 * @RabbitListener(queues = RabbitMQConfig.COLA_INSCRIPCIONES)
 * public void procesar(InscripcionMensaje mensaje) { ... }
 * }</pre>
 *
 * <p>Se usa {@code record} en lugar de clase porque:
 * <ul>
 *   <li>Los datos son inmutables una vez recibidos.</li>
 *   <li>Jackson puede deserializar records con constructor canónico.</li>
 *   <li>Reduce boilerplate eliminando getters, equals, hashCode y toString.</li>
 * </ul>
 */
public record InscripcionMensaje(

    /**
     * Identificador único del estudiante en el sistema de identidad.
     */
    String estudianteId,

    /**
     * Nombre completo del estudiante, aparecerá en el comprobante PDF.
     */
    String nombreEstudiante,

    /**
     * Correo electrónico del estudiante.
     */
    String emailEstudiante,

    /**
     * Lista de IDs de los cursos en los que el estudiante se inscribió.
     * El Worker usa estos IDs para consultar precios en Oracle Cloud.
     */
    List<Long> cursosIds

) {}
