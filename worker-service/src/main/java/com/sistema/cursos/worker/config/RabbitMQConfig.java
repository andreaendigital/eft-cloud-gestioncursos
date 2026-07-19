package com.sistema.cursos.worker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración RabbitMQ del Worker Service.
 *
 * <p>Declara los mismos componentes que el BFF Service (cola, exchange, binding)
 * para asegurar idempotencia: si el BFF ya los creó en el broker, RabbitMQ
 * simplemente verifica que existen con los mismos parámetros.
 *
 * <p>La configuración más crítica aquí es el {@link Jackson2JsonMessageConverter}
 * registrado tanto en el {@link SimpleRabbitListenerContainerFactory} como en
 * el {@link RabbitTemplate}: sin esto, el {@code @RabbitListener} recibiría
 * bytes crudos en lugar de un objeto {@code InscripcionMensaje} deserializado.
 */
@Configuration
public class RabbitMQConfig {

    /** Nombre de la cola consumida por el Worker Service. */
    public static final String COLA_INSCRIPCIONES    = "cola-inscripciones";

    /** Nombre del exchange que enruta mensajes desde el BFF. */
    public static final String EXCHANGE_INSCRIPCIONES = "inscripciones-exchange";

    /** Routing key que conecta el exchange con la cola. */
    public static final String ROUTING_KEY           = "cola-inscripciones";

    // ── Componentes de infraestructura RabbitMQ ───────────────────────────

    /**
     * Declara la cola durable {@code cola-inscripciones}.
     * Durable = true: la cola sobrevive a reinicios del broker.
     *
     * @return cola configurada
     */
    @Bean
    public Queue colaInscripciones() {
        return new Queue(COLA_INSCRIPCIONES, true);
    }

    /**
     * Declara el exchange DirectExchange {@code inscripciones-exchange}.
     *
     * @return exchange configurado
     */
    @Bean
    public DirectExchange inscripcionesExchange() {
        return new DirectExchange(EXCHANGE_INSCRIPCIONES);
    }

    /**
     * Crea el binding entre la cola y el exchange con la routing key definida.
     *
     * @param cola     la cola declarada como @Bean
     * @param exchange el exchange declarado como @Bean
     * @return binding configurado
     */
    @Bean
    public Binding bindingInscripciones(Queue cola, DirectExchange exchange) {
        return BindingBuilder
            .bind(cola)
            .to(exchange)
            .with(ROUTING_KEY);
    }

    // ── Conversor de mensajes ─────────────────────────────────────────────

    /**
     * Registra Jackson2JsonMessageConverter como conversor de mensajes.
     *
     * <p>Este bean es fundamental para el Worker Service: permite que
     * {@code @RabbitListener} reciba directamente un objeto Java
     * (en este caso {@code InscripcionMensaje}) en lugar de {@code byte[]},
     * eliminando la necesidad de deserialización manual en el listener.
     *
     * @return conversor JSON basado en Jackson
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configura el {@link RabbitTemplate} con el conversor JSON.
     * Necesario para publicaciones desde el Worker (ej. respuestas o notificaciones).
     *
     * @param connectionFactory factoría de conexiones provista por Spring Boot
     * @return RabbitTemplate con conversor JSON configurado
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * Configura el contenedor de listeners con el conversor JSON.
     *
     * <p>Al registrar el {@link Jackson2JsonMessageConverter} en el
     * {@link SimpleRabbitListenerContainerFactory}, Spring AMQP usará
     * Jackson para deserializar automáticamente los mensajes JSON
     * recibidos por cualquier {@code @RabbitListener} de este servicio.
     *
     * @param connectionFactory factoría de conexiones provista por Spring Boot
     * @return contenedor de listeners configurado con JSON converter
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
