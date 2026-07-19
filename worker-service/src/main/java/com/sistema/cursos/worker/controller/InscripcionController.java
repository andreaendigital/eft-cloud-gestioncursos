package com.sistema.cursos.worker.controller;

import com.sistema.cursos.worker.model.EstadoInscripcion;
import com.sistema.cursos.worker.model.Inscripcion;
import com.sistema.cursos.worker.service.CloudStorageService;
import com.sistema.cursos.worker.service.InscripcionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST del Worker Service para la gestión de inscripciones.
 *
 * <p>Expone los endpoints CRUD de administración sobre la entidad
 * {@link Inscripcion} persistida en Oracle Cloud:
 * <ul>
 *   <li>{@code GET    /api/v1/inscripciones/{id}}           — consultar inscripción</li>
 *   <li>{@code GET    /api/v1/inscripciones/{id}/descargar} — descargar PDF del comprobante</li>
 *   <li>{@code PUT    /api/v1/inscripciones/{id}}           — actualizar inscripción</li>
 *   <li>{@code DELETE /api/v1/inscripciones/{id}}           — eliminar inscripción</li>
 * </ul>
 *
 * <p>Los errores 404 son manejados por
 * {@link com.sistema.cursos.worker.exception.WorkerGlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/inscripciones")
public class InscripcionController {

    private static final Logger log = LoggerFactory.getLogger(InscripcionController.class);

    private final InscripcionService    inscripcionService;
    private final CloudStorageService   cloudStorageService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param inscripcionService  servicio de negocio de inscripciones
     * @param cloudStorageService servicio para descargar PDFs desde S3
     */
    public InscripcionController(
            InscripcionService inscripcionService,
            CloudStorageService cloudStorageService) {
        this.inscripcionService  = inscripcionService;
        this.cloudStorageService = cloudStorageService;
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────

    /**
     * Retorna los datos de una inscripción por su ID.
     *
     * @param id identificador de la inscripción en Oracle Cloud
     * @return HTTP 200 con el JSON de la entidad, o HTTP 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<InscripcionResponse> obtenerPorId(@PathVariable Long id) {
        log.debug("GET /api/v1/inscripciones/{}", id);

        Inscripcion inscripcion = inscripcionService.buscarPorId(id);
        return ResponseEntity.ok(InscripcionResponse.desde(inscripcion));
    }

    // ── GET /{id}/descargar ───────────────────────────────────────────────

    /**
     * Descarga el comprobante PDF de una inscripción desde AWS S3.
     *
     * @param id identificador de la inscripción cuyo PDF se descarga
     * @return HTTP 200 con el PDF en el body, o HTTP 404 si no existe o no tiene PDF
     */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargarComprobante(@PathVariable Long id) {
        log.info("GET /api/v1/inscripciones/{}/descargar", id);

        Inscripcion inscripcion = inscripcionService.buscarPorId(id);

        if (inscripcion.getArchivoS3Key() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(null);
        }

        byte[] pdfBytes = cloudStorageService.descargarPdf(inscripcion.getArchivoS3Key());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData(
            "attachment",
            "comprobante-inscripcion-" + id + ".pdf"
        );
        headers.setContentLength(pdfBytes.length);

        log.info("PDF descargado para inscripción [{}] — {} bytes", id, pdfBytes.length);
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    // ── PUT /{id} ─────────────────────────────────────────────────────────

    /**
     * Actualiza campos modificables de una inscripción existente.
     *
     * <p>Campos actualizables: {@code nombreEstudiante}, {@code emailEstudiante},
     * {@code estado}, {@code archivoS3Key}. Los campos de auditoría
     * ({@code id}, {@code fechaInscripcion}) son ignorados.
     *
     * @param id          identificador de la inscripción a actualizar
     * @param requestBody mapa con los campos a actualizar
     * @return HTTP 200 con la inscripción actualizada, o HTTP 404 si no existe
     */
    @PutMapping("/{id}")
    public ResponseEntity<InscripcionResponse> actualizar(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {

        log.info("PUT /api/v1/inscripciones/{} — campos: {}", id, requestBody.keySet());

        // Construir entidad parcial con solo los campos enviados
        Inscripcion datosNuevos = new Inscripcion();
        if (requestBody.containsKey("nombreEstudiante")) {
            datosNuevos.setNombreEstudiante(requestBody.get("nombreEstudiante"));
        }
        if (requestBody.containsKey("emailEstudiante")) {
            datosNuevos.setEmailEstudiante(requestBody.get("emailEstudiante"));
        }
        if (requestBody.containsKey("estado")) {
            try {
                datosNuevos.setEstado(EstadoInscripcion.valueOf(
                    requestBody.get("estado").toUpperCase()));
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().build();
            }
        }
        if (requestBody.containsKey("archivoS3Key")) {
            datosNuevos.setArchivoS3Key(requestBody.get("archivoS3Key"));
        }

        Inscripcion actualizada = inscripcionService.actualizar(id, datosNuevos);
        log.info("Inscripción [{}] actualizada correctamente", id);
        return ResponseEntity.ok(InscripcionResponse.desde(actualizada));
    }

    // ── DELETE /{id} ──────────────────────────────────────────────────────

    /**
     * Elimina una inscripción de Oracle Cloud y su PDF de AWS S3.
     *
     * @param id identificador de la inscripción a eliminar
     * @return HTTP 204 No Content si fue eliminada, o HTTP 404 si no existe
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("DELETE /api/v1/inscripciones/{}", id);

        inscripcionService.eliminar(id);

        log.info("Inscripción [{}] eliminada exitosamente", id);
        return ResponseEntity.noContent().build();
    }

    // ── DTO de respuesta ──────────────────────────────────────────────────

    /**
     * DTO de respuesta para los endpoints del Worker Service.
     * Serializa la entidad {@link Inscripcion} como JSON estructurado,
     * excluyendo detalles internos de JPA.
     */
    public record InscripcionResponse(
        Long id,
        String estudianteId,
        String nombreEstudiante,
        String emailEstudiante,
        java.math.BigDecimal totalPagado,
        String fechaInscripcion,
        String archivoS3Key,
        String estado,
        List<Long> cursosIds
    ) {
        /**
         * Convierte una entidad {@link Inscripcion} a su representación de respuesta.
         *
         * @param inscripcion entidad persistida en Oracle Cloud
         * @return DTO listo para serializar como JSON
         */
        public static InscripcionResponse desde(Inscripcion inscripcion) {
            return new InscripcionResponse(
                inscripcion.getId(),
                inscripcion.getEstudianteId(),
                inscripcion.getNombreEstudiante(),
                inscripcion.getEmailEstudiante(),
                inscripcion.getTotalPagado(),
                inscripcion.getFechaInscripcion() != null
                    ? inscripcion.getFechaInscripcion().toString() : null,
                inscripcion.getArchivoS3Key(),
                inscripcion.getEstado() != null
                    ? inscripcion.getEstado().name() : null,
                inscripcion.getCursosIds()
            );
        }
    }
}
