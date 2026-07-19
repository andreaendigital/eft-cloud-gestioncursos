package com.sistema.cursos.worker.repository;

import com.sistema.cursos.worker.model.EstadoInscripcion;
import com.sistema.cursos.worker.model.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para la entidad {@link Inscripcion}.
 *
 * <p>Hereda las operaciones CRUD estándar de {@link JpaRepository}.
 * El Worker Service usa principalmente:
 * <ul>
 *   <li>{@code save(Inscripcion)} — para persistir o actualizar una inscripción.</li>
 *   <li>{@code findById(Long)} — para los endpoints GET/PUT/DELETE.</li>
 *   <li>{@code deleteById(Long)} — para el endpoint DELETE.</li>
 * </ul>
 *
 * <p>Los métodos derivados adicionales siguen la convención de nombres
 * de Spring Data JPA y no requieren implementación manual.
 */
@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    /**
     * Busca todas las inscripciones de un estudiante por su identificador.
     *
     * @param estudianteId identificador del estudiante
     * @return lista de inscripciones del estudiante; puede ser vacía
     */
    List<Inscripcion> findByEstudianteId(String estudianteId);

    /**
     * Busca todas las inscripciones con un estado específico.
     * Útil para tareas de monitoreo y reporte de errores.
     *
     * @param estado estado de procesamiento a filtrar
     * @return lista de inscripciones con el estado indicado; puede ser vacía
     */
    List<Inscripcion> findByEstado(EstadoInscripcion estado);
}
