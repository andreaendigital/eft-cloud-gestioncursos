package com.sistema.cursos.worker.model;

/**
 * Enum que representa el estado de procesamiento de una inscripción.
 *
 * <p>Este valor es almacenado como texto ({@code VARCHAR2}) en la columna
 * {@code ESTADO} de la tabla {@code INSCRIPCIONES} mediante
 * {@link jakarta.persistence.EnumType#STRING}.
 *
 * <p>La restricción {@code CHECK (ESTADO IN ('PROCESADO','ERROR'))} en el
 * DDL de Oracle garantiza integridad referencial a nivel de base de datos.
 */
public enum EstadoInscripcion {

    /**
     * La inscripción fue procesada exitosamente:
     * precios consultados, total calculado, PDF generado,
     * archivo subido a S3 y datos persistidos en Oracle Cloud.
     */
    PROCESADO,

    /**
     * Ocurrió un error durante el procesamiento de la inscripción.
     * El detalle del fallo queda registrado en los logs del Worker Service.
     * La entidad {@code Inscripcion} es persistida con este estado
     * para garantizar trazabilidad sin reintentos infinitos en RabbitMQ.
     */
    ERROR
}
