package com.sistema.cursos.bff.service;

import com.sistema.cursos.bff.dto.InscripcionRequest;

/**
 * Contrato del servicio de mensajería del BFF Service.
 *
 * <p>Define la operación de publicación de solicitudes de inscripción
 * en la cola RabbitMQ. Separar la interfaz de la implementación
 * facilita pruebas unitarias con mocks y permite cambiar el proveedor
 * de mensajería sin modificar el controlador (principio Open/Closed).
 */
public interface MensajeService {

    /**
     * Publica una solicitud de inscripción en la cola {@code cola-inscripciones}.
     *
     * @param request DTO validado con los datos del estudiante y los IDs de cursos
     * @throws org.springframework.amqp.AmqpException si no se puede conectar al broker
     */
    void publicar(InscripcionRequest request);
}
