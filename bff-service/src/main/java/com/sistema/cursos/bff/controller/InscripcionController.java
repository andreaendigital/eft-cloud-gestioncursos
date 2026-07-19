package com.sistema.cursos.bff.controller;

import com.sistema.cursos.bff.dto.InscripcionRequest;
import com.sistema.cursos.bff.service.MensajeService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST del BFF Service para la gestión de inscripciones.
 *
 * <p>Expone el endpoint {@code POST /api/v1/inscripciones} que:
 * <ol>
 *   <li>Recibe el payload compacto del frontend.</li>
 *   <li>Delega la publicación asíncrona a {@link MensajeService}.</li>
 *   <li>Responde inmediatamente con HTTP 202 Accepted.</li>
 * </ol>
 *
 * <p>El controlador NO calcula precios, NO consulta la base de datos
 * y NO espera confirmación del Worker. Patrón fire-and-forget via RabbitMQ.
 */
@RestController
@RequestMapping("/api/v1/inscripciones")
public class InscripcionController {

    private static final Logger log = LoggerFactory.getLogger(InscripcionController.class);

    private final MensajeService mensajeService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param mensajeService servicio que publica mensajes en RabbitMQ
     */
    public InscripcionController(MensajeService mensajeService) {
        this.mensajeService = mensajeService;
    }

    /**
     * Recibe una solicitud de inscripción y la encola en RabbitMQ.
     *
     * <p>El payload es validado automáticamente por {@code @Valid} antes de
     * entrar al método. Si la validación falla, Spring lanza
     * {@link org.springframework.web.bind.MethodArgumentNotValidException}
     * que es capturada por {@link com.sistema.cursos.bff.exception.GlobalExceptionHandler}.
     *
     * @param request DTO validado con los datos del estudiante y lista de IDs de cursos
     * @return HTTP 202 Accepted con mensaje JSON indicando procesamiento asíncrono
     */
    @PostMapping
    public ResponseEntity<MensajeRespuesta> inscribir(
            @Valid @RequestBody InscripcionRequest request) {

        log.debug("POST /api/v1/inscripciones recibido para estudianteId: [{}]",
            request.estudianteId());

        mensajeService.publicar(request);

        MensajeRespuesta respuesta = new MensajeRespuesta(
            "Solicitud de inscripción recibida. Está siendo procesada de forma asíncrona.",
            request.estudianteId()
        );

        // HTTP 202 Accepted: la solicitud fue aceptada pero el procesamiento
        // aún no ha concluido (lo completa el Worker Service de forma asíncrona).
        return ResponseEntity.accepted().body(respuesta);
    }

    /**
     * Registro interno para la respuesta del endpoint POST.
     * Evita crear una clase separada para un DTO de respuesta simple.
     *
     * @param mensaje    texto descriptivo del estado de la solicitud
     * @param estudianteId identificador del estudiante que realizó la inscripción
     */
    public record MensajeRespuesta(String mensaje, String estudianteId) {}
}
