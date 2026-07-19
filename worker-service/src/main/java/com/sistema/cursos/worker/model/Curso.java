package com.sistema.cursos.worker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Entidad JPA que representa un curso disponible en la plataforma.
 *
 * <p>Mapeada a la tabla {@code CURSOS} creada por {@code schema.sql} en Oracle Cloud.
 * El Worker Service consulta esta entidad para obtener los precios vigentes
 * de los cursos al momento de procesar una inscripción, garantizando
 * que el costo cobrado sea el oficial en la base de datos.
 *
 * <p>La generación del ID usa la secuencia {@code SEQ_CURSOS} de Oracle,
 * compatible con el DDL definido en {@code schema.sql}.
 */
@Entity
@Table(name = "CURSOS")
public class Curso {

    @Id
    @GeneratedValue(
        strategy = GenerationType.SEQUENCE,
        generator = "seq_cursos_gen"
    )
    @SequenceGenerator(
        name            = "seq_cursos_gen",
        sequenceName    = "SEQ_CURSOS",
        allocationSize  = 1  // allocationSize=1 para sincronizar con INCREMENT BY 1 del DDL
    )
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "NOMBRE", nullable = false, length = 200)
    private String nombre;

    @Column(name = "PRECIO", nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "DESCRIPCION", length = 1000)
    private String descripcion;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo = 1;

    // ── Constructores ─────────────────────────────────────────────────────

    /** Constructor requerido por JPA. */
    protected Curso() {}

    /**
     * Constructor de conveniencia para pruebas y creación programática.
     *
     * @param nombre      nombre del curso
     * @param precio      precio en pesos (CLP)
     * @param descripcion descripción del contenido del curso
     */
    public Curso(String nombre, BigDecimal precio, String descripcion) {
        this.nombre      = nombre;
        this.precio      = precio;
        this.descripcion = descripcion;
        this.activo      = 1;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Integer getActivo() {
        return activo;
    }

    // ── Setters ───────────────────────────────────────────────────────────

    public void setId(Long id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void setActivo(Integer activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return "Curso{id=" + id + ", nombre='" + nombre + "', precio=" + precio + "}";
    }
}
