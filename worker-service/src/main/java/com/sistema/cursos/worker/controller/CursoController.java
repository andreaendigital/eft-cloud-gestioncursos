package com.sistema.cursos.worker.controller;

import com.sistema.cursos.worker.model.Curso;
import com.sistema.cursos.worker.repository.CursoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para consulta del catálogo de cursos.
 *
 * <p>Expone:
 * <ul>
 *   <li>{@code GET /api/v1/cursos}       — lista todos los cursos activos</li>
 *   <li>{@code GET /api/v1/cursos/{id}}  — consulta un curso por ID</li>
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

    /**
     * Retorna todos los cursos disponibles en el catálogo.
     *
     * @return HTTP 200 con lista JSON de cursos
     */
    @GetMapping
    public ResponseEntity<List<Curso>> listarTodos() {
        log.debug("GET /api/v1/cursos");
        List<Curso> cursos = cursoRepository.findAll();
        return ResponseEntity.ok(cursos);
    }

    /**
     * Retorna un curso por su ID.
     *
     * @param id identificador del curso
     * @return HTTP 200 con el curso, o HTTP 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<Curso> obtenerPorId(@PathVariable Long id) {
        log.debug("GET /api/v1/cursos/{}", id);
        Curso curso = cursoRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Curso no encontrado con ID: " + id));
        return ResponseEntity.ok(curso);
    }
}
