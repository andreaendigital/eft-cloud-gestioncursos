package com.sistema.cursos.bff.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Controlador proxy del BFF para el catálogo de cursos.
 *
 * <p>Recibe las peticiones en el BFF (puerto 8080) bajo {@code /api/v1/cursos}
 * y las delega internamente al Worker Service (puerto 8081), manteniendo
 * el patrón BFF: todo el tráfico externo pasa por el puerto 8080.
 *
 * <p>Endpoints expuestos:
 * <ul>
 *   <li>{@code GET /api/v1/cursos}      → lista todos los cursos</li>
 *   <li>{@code GET /api/v1/cursos/{id}} → obtiene un curso por ID</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cursos")
public class CursoController {

    private static final Logger log = LoggerFactory.getLogger(CursoController.class);

    private final RestTemplate restTemplate;
    private final String workerBaseUrl;

    /**
     * @param restTemplate   cliente HTTP para llamadas internas al Worker Service
     * @param workerBaseUrl  URL base del Worker Service, leída de application.yml
     */
    public CursoController(
            RestTemplate restTemplate,
            @Value("${worker.service.url:http://worker-service:8081}") String workerBaseUrl) {
        this.restTemplate   = restTemplate;
        this.workerBaseUrl  = workerBaseUrl;
    }

    /**
     * Proxy: lista todos los cursos disponibles.
     * Delega a {@code GET worker-service:8081/api/v1/cursos}.
     *
     * @return HTTP 200 con la lista de cursos en JSON
     */
    @GetMapping
    public ResponseEntity<Object> listarCursos() {
        String url = workerBaseUrl + "/api/v1/cursos";
        log.info("BFF proxy GET /api/v1/cursos -> {}", url);

        ResponseEntity<Object> respuesta = restTemplate.getForEntity(url, Object.class);
        return ResponseEntity.status(respuesta.getStatusCode()).body(respuesta.getBody());
    }

    /**
     * Proxy: obtiene un curso por su ID.
     * Delega a {@code GET worker-service:8081/api/v1/cursos/{id}}.
     *
     * @param id identificador del curso
     * @return HTTP 200 con el curso, o HTTP 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> obtenerCursoPorId(@PathVariable Long id) {
        String url = workerBaseUrl + "/api/v1/cursos/" + id;
        log.info("BFF proxy GET /api/v1/cursos/{} -> {}", id, url);

        ResponseEntity<Object> respuesta = restTemplate.getForEntity(url, Object.class);
        return ResponseEntity.status(respuesta.getStatusCode()).body(respuesta.getBody());
    }
}
