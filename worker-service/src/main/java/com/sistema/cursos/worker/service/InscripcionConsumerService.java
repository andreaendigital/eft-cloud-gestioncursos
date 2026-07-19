package com.sistema.cursos.worker.service;

import com.sistema.cursos.worker.config.RabbitMQConfig;
import com.sistema.cursos.worker.dto.InscripcionMensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Consumidor RabbitMQ del Worker Service.
 *
 * <p>Escucha la cola {@code cola-inscripciones} de forma continua mediante
 * {@code @RabbitListener}. Su única responsabilidad es recibir el mensaje
 * deserializado y delegarlo a {@link InscripcionService} (principio SRP).
 *
 * <p>El {@link com.sistema.cursos.worker.config.RabbitMQConfig} configura
 * {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}
 * en el {@code SimpleRabbitListenerContainerFactory}, permitiendo que Spring AMQP
 * deserialice automáticamente el payload JSON al objeto {@link InscripcionMensaje}.
 *
 * <p>Manejo de errores:
 * <ul>
 *   <li>Si la deserialización falla (JSON inválido), Spring AMQP registra el error
 *       y descarta el mensaje (no hace requeue).</li>
 *   <li>Si {@code procesarInscripcion} lanza una excepción, el {@code InscripcionServiceImpl}
 *       ya la captura internamente y persiste el estado {@code ERROR} — este consumer
 *       nunca verá la excepción propagarse.</li>
 * </ul>
 */
@Service
public class InscripcionConsumerService {

    private static final Logger log = LoggerFactory.getLogger(InscripcionConsumerService.class);

    private final InscripcionService inscripcionService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param inscripcionService servicio que orquesta el procesamiento completo
     */
    public InscripcionConsumerService(InscripcionService inscripcionService) {
        this.inscripcionService = inscripcionService;
    }

    /**
     * Recibe y procesa un mensaje de inscripción desde la cola RabbitMQ.
     *
     * <p>El método es invocado automáticamente por el contenedor de listeners
     * de Spring AMQP cada vez que llega un mensaje a {@code cola-inscripciones}.
     * Spring inyecta el objeto {@link InscripcionMensaje} ya deserializado
     * desde el JSON del mensaje.
     *
     * @param mensaje objeto deserializado desde el payload JSON de la cola;
     *                contiene estudianteId, nombreEstudiante, emailEstudiante y cursosIds
     */
    @RabbitListener(queues = RabbitMQConfig.COLA_INSCRIPCIONES)
    public void procesarMensaje(InscripcionMensaje mensaje) {
        log.info("Mensaje recibido de cola [{}] — estudianteId: [{}], cursos: {}",
            RabbitMQConfig.COLA_INSCRIPCIONES,
            mensaje.estudianteId(),
            mensaje.cursosIds()
        );

        // Delegación completa al servicio de negocio.
        // El servicio maneja todos los errores internamente y nunca relanza.
        inscripcionService.procesarInscripcion(mensaje);

        log.debug("Procesamiento delegado para estudianteId: [{}]", mensaje.estudianteId());
    }
}
