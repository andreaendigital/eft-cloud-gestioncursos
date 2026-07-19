package com.sistema.cursos.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del BFF Service.
 *
 * <p>Este microservicio actúa como Backend for Frontend (BFF):
 * <ul>
 *   <li>Valida tokens JWT emitidos por Azure AD (OAuth2 Resource Server).</li>
 *   <li>Recibe solicitudes de inscripción desde el frontend.</li>
 *   <li>Publica los datos en la cola RabbitMQ {@code cola-inscripciones}.</li>
 *   <li>NO tiene conexión directa a base de datos.</li>
 * </ul>
 */
@SpringBootApplication
public class BffServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffServiceApplication.class, args);
    }
}
