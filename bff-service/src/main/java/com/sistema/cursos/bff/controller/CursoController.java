package com.sistema.cursos.bff.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * Controlador proxy del BFF para el catálogo de cursos.
 *
 * <p>Todo el tráfico externo entra por el BFF (puerto 8080).
 * Este controlador reenvía las peticiones internamente al
 * Worker Service (puerto 8081), propagando el token JWT
 * para que la cadena de seguridad se mantenga.
 */
@RestController
@RequestMapping("/api/v1/cursos")
public class CursoController {

    private static final Logger log = LoggerFactory.getLogger(CursoController.class);

    private final RestTemplate restTemplate;
    private final String workerUrl;

    public CursoController(
            RestTemplate restTemplate,
            @Value("${worker.service.url:http://worker-service:8081}") String workerBaseUrl) {
        this.restTemplate = restTemplate;
        this.workerUrl    = workerBaseUrl + "/api/v1/cursos";
    }

    // ── POST /api/v1/cursos — Crear curso ─────────────────────────────────

    /**
     * Proxy: crea un nuevo curso en el catálogo.
     * Delega a POST worker-service:8081/api/v1/cursos.
     */
    @PostMapping
    public ResponseEntity<Object> crearCurso(
            @RequestBody Object cursoRequest,
            HttpServletRequest request) {

        log.info("BFF proxy POST /api/v1/cursos -> {}", workerUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        propagarAuth(headers, request);

        HttpEntity<Object> entity = new HttpEntity<>(cursoRequest, headers);
        return restTemplate.postForEntity(workerUrl, entity, Object.class);
    }

    // ── GET /api/v1/cursos — Listar todos ─────────────────────────────────

    /**
     * Proxy: lista todos los cursos disponibles.
     * Delega a GET worker-service:8081/api/v1/cursos.
     */
    @GetMapping
    public ResponseEntity<Object> listarCursos(HttpServletRequest request) {
        log.info("BFF proxy GET /api/v1/cursos -> {}", workerUrl);

        HttpHeaders headers = new HttpHeaders();
        propagarAuth(headers, request);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(workerUrl, HttpMethod.GET, entity, Object.class);
    }

    // ── GET /api/v1/cursos/{id} — Obtener por ID ─────────────────────────

    /**
     * Proxy: obtiene un curso por su ID.
     * Delega a GET worker-service:8081/api/v1/cursos/{id}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> obtenerCursoPorId(
            @PathVariable Long id,
            HttpServletRequest request) {

        String url = workerUrl + "/" + id;
        log.info("BFF proxy GET /api/v1/cursos/{} -> {}", id, url);

        HttpHeaders headers = new HttpHeaders();
        propagarAuth(headers, request);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);
    }

    // ── PUT /api/v1/cursos/{id} — Actualizar ─────────────────────────────

    /**
     * Proxy: actualiza un curso existente.
     * Delega a PUT worker-service:8081/api/v1/cursos/{id}.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Object> actualizarCurso(
            @PathVariable Long id,
            @RequestBody Object cursoRequest,
            HttpServletRequest request) {

        String url = workerUrl + "/" + id;
        log.info("BFF proxy PUT /api/v1/cursos/{} -> {}", id, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        propagarAuth(headers, request);

        HttpEntity<Object> entity = new HttpEntity<>(cursoRequest, headers);
        return restTemplate.exchange(url, HttpMethod.PUT, entity, Object.class);
    }

    // ── DELETE /api/v1/cursos/{id} — Eliminar ────────────────────────────

    /**
     * Proxy: elimina un curso por su ID.
     * Delega a DELETE worker-service:8081/api/v1/cursos/{id}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> eliminarCurso(
            @PathVariable Long id,
            HttpServletRequest request) {

        String url = workerUrl + "/" + id;
        log.info("BFF proxy DELETE /api/v1/cursos/{} -> {}", id, url);

        HttpHeaders headers = new HttpHeaders();
        propagarAuth(headers, request);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, Object.class);
    }

    // ── Método auxiliar ───────────────────────────────────────────────────

    /**
     * Propaga el header Authorization de la petición entrante hacia el Worker.
     * Garantiza que el token JWT viaje en todas las llamadas internas.
     */
    private void propagarAuth(HttpHeaders headers, HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }
    }
}
