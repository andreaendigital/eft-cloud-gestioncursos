package com.sistema.cursos.bff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO de entrada para la solicitud de inscripción.
 *
 * <p>Representa el payload compacto que el frontend envía al BFF Service.
 * El BFF publica este objeto directamente en RabbitMQ sin modificaciones.
 * Toda la lógica de negocio (consulta de precios, cálculo de total)
 * es responsabilidad del Worker Service.
 *
 * <p>Ejemplo de payload esperado:
 * <pre>{@code
 * {
 *   "estudianteId": "1024",
 *   "nombreEstudiante": "Juan Pérez",
 *   "emailEstudiante": "juan.perez@email.com",
 *   "cursosIds": [5, 12, 43]
 * }
 * }</pre>
 *
 * <p>Validaciones aplicadas:
 * <ul>
 *   <li>{@code estudianteId}: no puede ser nulo ni vacío.</li>
 *   <li>{@code nombreEstudiante}: no puede ser nulo ni vacío.</li>
 *   <li>{@code emailEstudiante}: no puede ser nulo/vacío y debe tener formato de email válido.</li>
 *   <li>{@code cursosIds}: la lista no puede ser nula ni estar vacía.</li>
 * </ul>
 */
public record InscripcionRequest(

    /**
     * Identificador único del estudiante en el sistema.
     * Proviene del sistema de identidad (Azure AD u otro IdP).
     */
    @NotBlank(message = "El campo 'estudianteId' es obligatorio y no puede estar vacío.")
    String estudianteId,

    /**
     * Nombre completo del estudiante tal como se mostrará en el comprobante.
     */
    @NotBlank(message = "El campo 'nombreEstudiante' es obligatorio y no puede estar vacío.")
    String nombreEstudiante,

    /**
     * Correo electrónico del estudiante para notificaciones y el comprobante PDF.
     */
    @NotBlank(message = "El campo 'emailEstudiante' es obligatorio y no puede estar vacío.")
    @Email(message = "El campo 'emailEstudiante' debe tener un formato de correo electrónico válido.")
    String emailEstudiante,

    /**
     * Lista de identificadores de los cursos en los que el estudiante desea inscribirse.
     * El Worker Service usará estos IDs para consultar precios en Oracle Cloud.
     */
    @NotEmpty(message = "La lista 'cursosIds' es obligatoria y debe contener al menos un curso.")
    List<Long> cursosIds

) {}
