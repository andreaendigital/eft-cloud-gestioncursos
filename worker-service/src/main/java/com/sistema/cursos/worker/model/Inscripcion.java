package com.sistema.cursos.worker.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa el registro de inscripción de un estudiante.
 *
 * <p>Mapeada a la tabla {@code INSCRIPCIONES} definida en {@code schema.sql}.
 * El Worker Service crea y persiste esta entidad tras procesar de forma
 * asíncrona el mensaje consumido de la cola {@code cola-inscripciones}.
 *
 * <p>La relación con los cursos se almacena en la tabla intermedia
 * {@code INSCRIPCION_CURSOS} mediante {@code @ElementCollection}, evitando
 * la necesidad de una entidad de unión completa para esta colección simple.
 *
 * <p>El campo {@code estado} refleja el resultado del procesamiento:
 * {@link EstadoInscripcion#PROCESADO} o {@link EstadoInscripcion#ERROR}.
 */
@Entity
@Table(name = "INSCRIPCIONES")
public class Inscripcion {

    @Id
    @GeneratedValue(
        strategy  = GenerationType.SEQUENCE,
        generator = "seq_inscripciones_gen"
    )
    @SequenceGenerator(
        name           = "seq_inscripciones_gen",
        sequenceName   = "SEQ_INSCRIPCIONES",
        allocationSize = 1  // Sincronizado con INCREMENT BY 1 del DDL Oracle
    )
    @Column(name = "ID", nullable = false)
    private Long id;

    /** Identificador único del estudiante proveniente del payload del BFF. */
    @Column(name = "ESTUDIANTE_ID", nullable = false, length = 100)
    private String estudianteId;

    /** Nombre completo del estudiante, tal como aparece en el comprobante PDF. */
    @Column(name = "NOMBRE_ESTUDIANTE", nullable = false, length = 200)
    private String nombreEstudiante;

    /** Correo electrónico del estudiante para notificaciones. */
    @Column(name = "EMAIL_ESTUDIANTE", nullable = false, length = 200)
    private String emailEstudiante;

    /**
     * Total calculado por el Worker sumando los precios de cada curso.
     * Usa {@link BigDecimal} para aritmética exacta (sin errores de punto flotante).
     */
    @Column(name = "TOTAL_PAGADO", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPagado;

    /** Timestamp de procesamiento asignado automáticamente por {@link #prePersist()}. */
    @Column(name = "FECHA_INSCRIPCION", nullable = false)
    private LocalDateTime fechaInscripcion;

    /**
     * Clave S3 del comprobante PDF subido por {@code CloudStorageService}.
     * Formato: {@code resumen-{id}/comprobante.pdf}.
     * Puede ser {@code null} si la subida a S3 falló (estado {@code ERROR}).
     */
    @Column(name = "ARCHIVO_S3_KEY", length = 500)
    private String archivoS3Key;

    /**
     * Estado del procesamiento asíncrono.
     * Almacenado como texto {@code VARCHAR2} en Oracle
     * (valores posibles: {@code PROCESADO} o {@code ERROR}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoInscripcion estado;

    /**
     * Lista de IDs de cursos incluidos en esta inscripción.
     *
     * <p>Mapeada con {@code @ElementCollection} a la tabla intermedia
     * {@code INSCRIPCION_CURSOS}. No requiere entidad separada porque
     * los IDs son escalares (Long), no objetos de dominio completos.
     *
     * <p>{@code FetchType.EAGER} garantiza que los IDs estén disponibles
     * inmediatamente tras cargar la entidad {@code Inscripcion}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name        = "INSCRIPCION_CURSOS",
        joinColumns = @JoinColumn(name = "INSCRIPCION_ID")
    )
    @Column(name = "CURSO_ID", nullable = false)
    private List<Long> cursosIds = new ArrayList<>();

    // ── Callbacks de ciclo de vida JPA ────────────────────────────────────

    /**
     * Establece automáticamente la fecha de inscripción antes del primer INSERT.
     * Garantiza que el campo nunca quede {@code null} sin depender del DEFAULT de Oracle.
     */
    @PrePersist
    protected void prePersist() {
        if (this.fechaInscripcion == null) {
            this.fechaInscripcion = LocalDateTime.now();
        }
    }

    // ── Constructores ─────────────────────────────────────────────────────

    /** Constructor requerido por JPA. */
    protected Inscripcion() {}

    /**
     * Constructor completo para crear una inscripción desde el Worker Service.
     *
     * @param estudianteId     identificador del estudiante
     * @param nombreEstudiante nombre completo del estudiante
     * @param emailEstudiante  correo electrónico del estudiante
     * @param totalPagado      suma de precios de los cursos inscritos
     * @param cursosIds        lista de IDs de cursos incluidos
     * @param estado           resultado del procesamiento ({@code PROCESADO} o {@code ERROR})
     */
    public Inscripcion(
            String estudianteId,
            String nombreEstudiante,
            String emailEstudiante,
            BigDecimal totalPagado,
            List<Long> cursosIds,
            EstadoInscripcion estado) {
        this.estudianteId     = estudianteId;
        this.nombreEstudiante = nombreEstudiante;
        this.emailEstudiante  = emailEstudiante;
        this.totalPagado      = totalPagado;
        this.cursosIds        = cursosIds != null ? cursosIds : new ArrayList<>();
        this.estado           = estado;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getEstudianteId() { return estudianteId; }

    public String getNombreEstudiante() { return nombreEstudiante; }

    public String getEmailEstudiante() { return emailEstudiante; }

    public BigDecimal getTotalPagado() { return totalPagado; }

    public LocalDateTime getFechaInscripcion() { return fechaInscripcion; }

    public String getArchivoS3Key() { return archivoS3Key; }

    public EstadoInscripcion getEstado() { return estado; }

    public List<Long> getCursosIds() { return cursosIds; }

    // ── Setters ───────────────────────────────────────────────────────────

    public void setId(Long id) { this.id = id; }

    public void setEstudianteId(String estudianteId) { this.estudianteId = estudianteId; }

    public void setNombreEstudiante(String nombreEstudiante) {
        this.nombreEstudiante = nombreEstudiante;
    }

    public void setEmailEstudiante(String emailEstudiante) {
        this.emailEstudiante = emailEstudiante;
    }

    public void setTotalPagado(BigDecimal totalPagado) { this.totalPagado = totalPagado; }

    public void setFechaInscripcion(LocalDateTime fechaInscripcion) {
        this.fechaInscripcion = fechaInscripcion;
    }

    public void setArchivoS3Key(String archivoS3Key) { this.archivoS3Key = archivoS3Key; }

    public void setEstado(EstadoInscripcion estado) { this.estado = estado; }

    public void setCursosIds(List<Long> cursosIds) { this.cursosIds = cursosIds; }

    @Override
    public String toString() {
        return "Inscripcion{id=" + id
            + ", estudianteId='" + estudianteId + "'"
            + ", estado=" + estado
            + ", totalPagado=" + totalPagado + "}";
    }
}
