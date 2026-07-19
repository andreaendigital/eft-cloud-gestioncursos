package com.sistema.cursos.bff.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la infraestructura RabbitMQ para el BFF Service.
 *
 * <p>Declara los elementos necesarios para que el BFF pueda publicar
 * mensajes de inscripción de forma estructurada como JSON:
 * <ul>
 *   <li>{@code cola-inscripciones}: cola durable que persiste mensajes.</li>
 *   <li>{@code inscripciones-exchange}: exchange de tipo Direct.</li>
 *   <li>Binding entre la cola y el exchange usando la routing key {@code cola-inscripciones}.</li>
 *   <li>{@link Jackson2JsonMessageConverter}: serializa objetos Java a JSON en los mensajes.</li>
 * </ul>
 */
@Configuration
public class RabbitMQConfig {

    /** Nombre de la cola de inscripciones consumida por el Worker Service. */
    public static final String COLA_INSCRIPCIONES = "cola-inscripciones";

    /** Nombre del exchange de tipo Direct para enrutar inscripciones. */
    public static final String EXCHANGE_INSCRIPCIONES = "inscripciones-exchange";

    /** Routing key usada para enlazar el exchange con la cola. */
    public static final String ROUTING_KEY = "cola-inscripciones";

    // ── Declaración de componentes RabbitMQ ──────────────────────────────

    /**
     * Declara la cola {@code cola-inscripciones} como durable.
     * Una cola durable sobrevive a reinicios del broker RabbitMQ.
     *
     * @return la cola configurada
     */
    @Bean
    public Queue colaInscripciones() {
        // durable=true: la cola persiste si RabbitMQ se reinicia
        return new Queue(COLA_INSCRIPCIONES, true);
    }

    /**
     * Declara el exchange de tipo Direct {@code inscripciones-exchange}.
     * Un DirectExchange enruta mensajes a colas cuya routing key coincide exactamente.
     *
     * @return el exchange configurado
     */
    @Bean
    public DirectExchange inscripcionesExchange() {
        return new DirectExchange(EXCHANGE_INSCRIPCIONES);
    }

    /**
     * Crea el binding entre la cola y el exchange usando la routing key definida.
     * El mensaje publicado al exchange con la routing key {@code cola-inscripciones}
     * llegará exactamente a la cola {@code cola-inscripciones}.
     *
     * @param cola     la cola declarada como @Bean
     * @param exchange el exchange declarado como @Bean
     * @return el binding configurado
     */
    @Bean
    public Binding bindingInscripciones(Queue cola, DirectExchange exchange) {
        return BindingBuilder
            .bind(cola)
            .to(exchange)
            .with(ROUTING_KEY);
    }

    // ── Configuración del conversor de mensajes ───────────────────────────

    /**
     * Registra Jackson como conversor de mensajes RabbitMQ.
     * Permite serializar y deserializar objetos Java como JSON estructurado
     * en lugar del formato binario por defecto de Java (ObjectOutputStream).
     *
     * @return el conversor JSON basado en Jackson
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configura el {@link RabbitTemplate} con el conversor JSON.
     * Sobreescribe el bean por defecto de Spring Boot para que todas las
     * publicaciones usen JSON automáticamente.
     *
     * @param connectionFactory factoría de conexiones provista por Spring Boot
     * @return el RabbitTemplate configurado con el conversor JSON
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
