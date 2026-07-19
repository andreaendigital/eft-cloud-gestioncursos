package com.sistema.cursos.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del Worker Service.
 *
 * <p>Este microservicio actúa como procesador asíncrono:
 * <ul>
 *   <li>Consume mensajes de la cola RabbitMQ {@code cola-inscripciones}.</li>
 *   <li>Consulta precios de cursos en Oracle Cloud (Spring Data JPA).</li>
 *   <li>Calcula el total a pagar.</li>
 *   <li>Genera un comprobante PDF con OpenPDF.</li>
 *   <li>Sube el PDF a AWS S3 con AWS SDK v2.</li>
 *   <li>Persiste la entidad {@code Inscripcion} en Oracle Cloud.</li>
 *   <li>Expone endpoints REST para consulta, actualización y eliminación.</li>
 * </ul>
 */
@SpringBootApplication
public class WorkerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerServiceApplication.class, args);
    }
}
