package com.sistema.cursos.worker.controller;

import com.sistema.cursos.worker.model.Curso;
import com.sistema.cursos.worker.repository.CursoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST del Worker Service para el catálogo de cursos.
 *
 * <p>Expone CRUD completo sobre la entidad {@link Curso}:
 * <ul>
 *   <li>{@code GET    /api/v1/cursos}       — lista todos los cursos</li>
 *   <li>{@code GET    /api/v1/cursos/{id}}  — obtiene un curso por ID</li>
 *   <li>{@code POST   /api/v1/cursos}       — crea un nuevo curso</li>
 *   <li>{@code PUT    /api/v1/cursos/{id}}  — actualiza un curso existente</li>
 *   <li>{@code DELETE /api/v1/cursos/{id}}  — elimina un curso</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cursos")
public class CursoController {

    private static final Logger log = LoggerFactory.getLogger(CursoController.class);

    private final CursoRepository cursoRepository;

    public CursoController(CursoRepository cursoRepository) {
        this.cursoRepository = cursoRepository;
    }

    // ── GET — Listar todos ────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Curso>> listarTodos() {
        log.debug("GET /api/v1/cursos");
        return ResponseEntity.ok(cursoRepository.findAll());
    }

    // ── GET /{id} — Obtener por ID ────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<Curso> obtenerPorId(@PathVariable Long id) {
        log.debug("GET /api/v1/cursos/{}", id);
        Curso curso = cursoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Curso no encontrado con ID: " + id));
        return ResponseEntity.ok(curso);
    }

    // ── POST — Crear curso ────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Curso> crearCurso(@RequestBody Curso curso) {
        log.info("POST /api/v1/cursos — nombre: {}", curso.getNombre());
        Curso guardado = cursoRepository.save(curso);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    // ── PUT /{id} — Actualizar curso ──────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<Curso> actualizarCurso(
            @PathVariable Long id,
            @RequestBody Map<String, Object> campos) {

        log.info("PUT /api/v1/cursos/{}", id);

        Curso existente = cursoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Curso no encontrado con ID: " + id));

        if (campos.containsKey("nombre")) {
            existente.setNombre((String) campos.get("nombre"));
        }
        if (campos.containsKey("descripcion")) {
            existente.setDescripcion((String) campos.get("descripcion"));
        }
        if (campos.containsKey("precio")) {
            existente.setPrecio(
                new java.math.BigDecimal(campos.get("precio").toString()));
        }
        if (campos.containsKey("activo")) {
            existente.setActivo(
                Integer.valueOf(campos.get("activo").toString()));
        }

        return ResponseEntity.ok(cursoRepository.save(existente));
    }

    // ── DELETE /{id} — Eliminar curso ─────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCurso(@PathVariable Long id) {
        log.info("DELETE /api/v1/cursos/{}", id);

        if (!cursoRepository.existsById(id)) {
            throw new EntityNotFoundException("Curso no encontrado con ID: " + id);
        }
        cursoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
