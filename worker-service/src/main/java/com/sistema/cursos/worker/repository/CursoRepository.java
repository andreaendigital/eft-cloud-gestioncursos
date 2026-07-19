package com.sistema.cursos.worker.repository;

import com.sistema.cursos.worker.model.Curso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para la entidad {@link Curso}.
 *
 * <p>Hereda las operaciones CRUD estándar de {@link JpaRepository}:
 * {@code save()}, {@code findById()}, {@code findAll()}, {@code deleteById()}, etc.
 *
 * <p>El método más relevante para el Worker Service es {@code findAllById(Iterable<Long>)},
 * heredado de {@link org.springframework.data.repository.CrudRepository},
 * que permite recuperar múltiples cursos por sus IDs en una sola consulta SQL
 * (optimizado con {@code WHERE ID IN (...)}).
 *
 * <p>Ejemplo de uso en {@code InscripcionService}:
 * <pre>{@code
 * List<Curso> cursos = cursoRepository.findAllById(mensaje.cursosIds());
 * }</pre>
 */
@Repository
public interface CursoRepository extends JpaRepository<Curso, Long> {

    /**
     * Busca todos los cursos activos (ACTIVO = 1) cuyo ID esté en la lista proporcionada.
     *
     * <p>Usado por el Worker Service para validar que los cursos solicitados
     * estén disponibles y obtener sus precios vigentes.
     *
     * @param ids lista de identificadores de cursos a buscar
     * @return lista de cursos activos que coincidan con los IDs; puede ser vacía
     */
    @Query("SELECT c FROM Curso c WHERE c.id IN :ids AND c.activo = 1")
    List<Curso> findActivosByIdIn(List<Long> ids);
}
