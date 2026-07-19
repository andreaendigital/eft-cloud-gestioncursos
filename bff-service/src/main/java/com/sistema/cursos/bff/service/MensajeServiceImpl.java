package com.sistema.cursos.bff.service;

import com.sistema.cursos.bff.config.RabbitMQConfig;
import com.sistema.cursos.bff.dto.InscripcionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Implementación del servicio de mensajería.
 *
 * <p>Usa {@link RabbitTemplate} (configurado con {@link com.sistema.cursos.bff.config.RabbitMQConfig})
 * para publicar el DTO de inscripción como mensaje JSON en el exchange
 * {@code inscripciones-exchange} con la routing key {@code cola-inscripciones}.
 *
 * <p>Principios SOLID aplicados:
 * <ul>
 *   <li>SRP: esta clase tiene una única responsabilidad — publicar mensajes.</li>
 *   <li>DIP: depende de la abstracción {@link MensajeService}, no de sí misma.</li>
 * </ul>
 */
@Service
public class MensajeServiceImpl implements MensajeService {

    private static final Logger log = LoggerFactory.getLogger(MensajeServiceImpl.class);

    private final RabbitTemplate rabbitTemplate;

    /**
     * Constructor con inyección de dependencias.
     * Spring inyecta el {@link RabbitTemplate} configurado en {@link RabbitMQConfig}.
     *
     * @param rabbitTemplate cliente RabbitMQ configurado con Jackson JSON converter
     */
    public MensajeServiceImpl(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>El método:
     * <ol>
     *   <li>Registra en log los datos de la solicitud antes de publicar.</li>
     *   <li>Convierte el DTO a JSON usando {@link com.fasterxml.jackson.databind.ObjectMapper}
     *       configurado en el {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}.</li>
     *   <li>Publica el mensaje al exchange {@code inscripciones-exchange}
     *       con routing key {@code cola-inscripciones}.</li>
     *   <li>Registra confirmación del envío o propaga la excepción si falla.</li>
     * </ol>
     *
     * @param request DTO validado con los datos del estudiante y los IDs de cursos
     * @throws AmqpException si el broker no está disponible o la publicación falla
     */
    @Override
    public void publicar(InscripcionRequest request) {
        log.info("Publicando solicitud de inscripción en RabbitMQ — " +
                 "estudianteId: [{}], nombre: [{}], cursos: {}",
            request.estudianteId(),
            request.nombreEstudiante(),
            request.cursosIds()
        );

        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_INSCRIPCIONES,
                RabbitMQConfig.ROUTING_KEY,
                request
            );

            log.info("Mensaje publicado exitosamente en exchange [{}] con routing key [{}] " +
                     "para estudianteId: [{}]",
                RabbitMQConfig.EXCHANGE_INSCRIPCIONES,
                RabbitMQConfig.ROUTING_KEY,
                request.estudianteId()
            );

        } catch (AmqpException ex) {
            log.error("Error al publicar mensaje en RabbitMQ para estudianteId: [{}] — {}",
                request.estudianteId(),
                ex.getMessage(),
                ex
            );
            // Re-lanzar para que el GlobalExceptionHandler responda con HTTP 503
            throw ex;
        }
    }
}
