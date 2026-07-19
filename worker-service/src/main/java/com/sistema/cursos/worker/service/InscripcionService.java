package com.sistema.cursos.worker.service;

import com.sistema.cursos.worker.dto.InscripcionMensaje;
import com.sistema.cursos.worker.model.Inscripcion;

/**
 * Contrato del servicio de procesamiento de inscripciones.
 *
 * <p>Define las operaciones que el Worker Service expone tanto al
 * consumidor RabbitMQ ({@code InscripcionConsumerService}) como al
 * controlador REST ({@code InscripcionController}).
 *
 * <p>Separar interfaz de implementación permite:
 * <ul>
 *   <li>Mockear el servicio en pruebas sin necesidad de Oracle ni S3.</li>
 *   <li>Cambiar la implementación sin afectar los consumidores (Open/Closed).</li>
 * </ul>
 */
public interface InscripcionService {

    /**
     * Procesa de forma asíncrona una solicitud de inscripción recibida desde RabbitMQ.
     *
     * <p>Flujo completo:
     * <ol>
     *   <li>Consulta precios de los cursos en Oracle Cloud.</li>
     *   <li>Valida que todos los IDs de cursos existen.</li>
     *   <li>Calcula el total a pagar.</li>
     *   <li>Persiste la inscripción inicial con estado {@code ERROR} (para obtener ID).</li>
     *   <li>Genera el comprobante PDF con OpenPDF.</li>
     *   <li>Sube el PDF a AWS S3.</li>
     *   <li>Actualiza la inscripción con la clave S3 y estado {@code PROCESADO}.</li>
     * </ol>
     *
     * <p>Ante cualquier error, la inscripción queda persistida con estado {@code ERROR}
     * para auditoría, sin relanzar la excepción (evita requeue infinito en RabbitMQ).
     *
     * @param mensaje datos de inscripción deserializados desde el mensaje JSON de la cola
     */
    void procesarInscripcion(InscripcionMensaje mensaje);

    /**
     * Busca una inscripción por su identificador único.
     *
     * @param id identificador de la inscripción en Oracle Cloud
     * @return entidad {@link Inscripcion} encontrada
     * @throws jakarta.persistence.EntityNotFoundException si el ID no existe
     */
    Inscripcion buscarPorId(Long id);

    /**
     * Actualiza campos modificables de una inscripción existente.
     *
     * @param id          identificador de la inscripción a actualizar
     * @param datosNuevos objeto con los campos a actualizar
     * @return entidad {@link Inscripcion} actualizada y persistida
     * @throws jakarta.persistence.EntityNotFoundException si el ID no existe
     */
    Inscripcion actualizar(Long id, Inscripcion datosNuevos);

    /**
     * Elimina una inscripción de Oracle Cloud y su PDF asociado de S3.
     *
     * @param id identificador de la inscripción a eliminar
     * @throws jakarta.persistence.EntityNotFoundException si el ID no existe
     */
    void eliminar(Long id);
}
