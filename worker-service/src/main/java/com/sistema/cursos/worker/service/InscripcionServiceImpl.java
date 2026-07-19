package com.sistema.cursos.worker.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sistema.cursos.worker.dto.InscripcionMensaje;
import com.sistema.cursos.worker.model.Curso;
import com.sistema.cursos.worker.model.EstadoInscripcion;
import com.sistema.cursos.worker.model.Inscripcion;
import com.sistema.cursos.worker.repository.CursoRepository;
import com.sistema.cursos.worker.repository.InscripcionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implementación del servicio de procesamiento de inscripciones.
 *
 * <p>Orquesta el flujo asíncrono completo tras consumir un mensaje de RabbitMQ:
 * consulta precios, calcula total, genera PDF con OpenPDF, sube a S3 y persiste
 * en Oracle Cloud. Cualquier falla en el proceso es capturada y almacenada
 * como estado {@code ERROR} para trazabilidad sin requeue infinito.
 */
@Service
public class InscripcionServiceImpl implements InscripcionService {

    private static final Logger log = LoggerFactory.getLogger(InscripcionServiceImpl.class);

    // Formato para la fecha en el PDF y en los logs
    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final CursoRepository        cursoRepository;
    private final InscripcionRepository  inscripcionRepository;
    private final CloudStorageService    cloudStorageService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param cursoRepository       repositorio de cursos (Oracle Cloud)
     * @param inscripcionRepository repositorio de inscripciones (Oracle Cloud)
     * @param cloudStorageService   servicio de almacenamiento PDF en AWS S3
     */
    public InscripcionServiceImpl(
            CursoRepository cursoRepository,
            InscripcionRepository inscripcionRepository,
            CloudStorageService cloudStorageService) {
        this.cursoRepository       = cursoRepository;
        this.inscripcionRepository = inscripcionRepository;
        this.cloudStorageService   = cloudStorageService;
    }

    // ── procesarInscripcion ───────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Patrón de persistencia de dos fases:
     * <ol>
     *   <li>Primer {@code save()}: persiste con estado {@code ERROR} para obtener el ID
     *       generado por la secuencia Oracle (necesario para la ruta S3).</li>
     *   <li>Segundo {@code save()}: actualiza con la clave S3 y estado {@code PROCESADO}
     *       si todo el procesamiento fue exitoso.</li>
     * </ol>
     *
     * <p>El bloque {@code catch} garantiza que la inscripción siempre quede
     * persistida (con estado {@code ERROR}) incluso si falla S3 o la generación del PDF,
     * evitando el requeue infinito que ocurriría si se relanzara la excepción.
     */
    @Override
    @Transactional
    public void procesarInscripcion(InscripcionMensaje mensaje) {
        log.info("Iniciando procesamiento de inscripción — estudianteId: [{}], cursos: {}",
            mensaje.estudianteId(), mensaje.cursosIds());

        Inscripcion inscripcion = null;

        try {
            // ── Paso 1: Consultar precios de cursos en Oracle Cloud ───────
            List<Curso> cursos = cursoRepository.findAllById(mensaje.cursosIds());
            validarCursosEncontrados(cursos, mensaje.cursosIds());

            // ── Paso 2: Calcular total ────────────────────────────────────
            BigDecimal totalPagado = calcularTotal(cursos);
            log.debug("Total calculado: {} para {} curso(s)", totalPagado, cursos.size());

            // ── Paso 3: Persistir inscripción inicial con estado ERROR ─────
            // Se guarda primero para obtener el ID de la secuencia Oracle,
            // que se usa como nombre de carpeta en S3.
            inscripcion = new Inscripcion(
                mensaje.estudianteId(),
                mensaje.nombreEstudiante(),
                mensaje.emailEstudiante(),
                totalPagado,
                mensaje.cursosIds(),
                EstadoInscripcion.ERROR  // estado inicial defensivo
            );
            inscripcion = inscripcionRepository.save(inscripcion);
            log.debug("Inscripción persistida con ID [{}] y estado inicial ERROR",
                inscripcion.getId());

            // ── Paso 4: Generar PDF con OpenPDF ───────────────────────────
            byte[] pdfBytes = generarPdf(inscripcion, cursos, totalPagado);
            log.debug("PDF generado: {} bytes para inscripcionId: [{}]",
                pdfBytes.length, inscripcion.getId());

            // ── Paso 5: Subir PDF a AWS S3 ────────────────────────────────
            String claveS3 = cloudStorageService.subirPdf(pdfBytes, inscripcion.getId());
            log.info("PDF subido a S3 con clave: [{}]", claveS3);

            // ── Paso 6: Actualizar inscripción con clave S3 y estado PROCESADO ──
            inscripcion.setArchivoS3Key(claveS3);
            inscripcion.setEstado(EstadoInscripcion.PROCESADO);
            inscripcionRepository.save(inscripcion);

            log.info("Inscripción [{}] procesada exitosamente — estudianteId: [{}], total: {}",
                inscripcion.getId(), mensaje.estudianteId(), totalPagado);

        } catch (Exception ex) {
            // ── Manejo de errores: persistir con estado ERROR para auditoría ──
            log.error("Error al procesar inscripción para estudianteId: [{}] — {}",
                mensaje.estudianteId(), ex.getMessage(), ex);

            // Si la inscripción ya fue persistida, actualizar su estado a ERROR
            if (inscripcion != null && inscripcion.getId() != null) {
                try {
                    inscripcion.setEstado(EstadoInscripcion.ERROR);
                    inscripcionRepository.save(inscripcion);
                    log.warn("Inscripción [{}] marcada con estado ERROR en Oracle Cloud",
                        inscripcion.getId());
                } catch (Exception saveEx) {
                    log.error("No se pudo actualizar estado ERROR para inscripción [{}]: {}",
                        inscripcion.getId(), saveEx.getMessage());
                }
            } else {
                // Si no se llegó a persistir, intentar guardar con datos mínimos
                persistirConError(mensaje);
            }
            // NO relanzar: evita requeue infinito en RabbitMQ
        }
    }

    // ── buscarPorId ───────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Inscripcion buscarPorId(Long id) {
        return inscripcionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Inscripción no encontrada con ID: " + id));
    }

    // ── actualizar ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Solo actualiza campos modificables para no sobrescribir datos
     * de auditoría (ID, fechaInscripcion, archivoS3Key generado por S3).
     */
    @Override
    @Transactional
    public Inscripcion actualizar(Long id, Inscripcion datosNuevos) {
        Inscripcion existente = inscripcionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Inscripción no encontrada con ID: " + id));

        // Actualizar solo campos permitidos
        if (datosNuevos.getNombreEstudiante() != null) {
            existente.setNombreEstudiante(datosNuevos.getNombreEstudiante());
        }
        if (datosNuevos.getEmailEstudiante() != null) {
            existente.setEmailEstudiante(datosNuevos.getEmailEstudiante());
        }
        if (datosNuevos.getEstado() != null) {
            existente.setEstado(datosNuevos.getEstado());
        }
        if (datosNuevos.getArchivoS3Key() != null) {
            existente.setArchivoS3Key(datosNuevos.getArchivoS3Key());
        }

        Inscripcion actualizada = inscripcionRepository.save(existente);
        log.info("Inscripción [{}] actualizada correctamente", id);
        return actualizada;
    }

    // ── eliminar ──────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Primero intenta eliminar el PDF de S3 (fallo no bloquea el DELETE en Oracle).
     * Luego elimina la entidad de la base de datos.
     */
    @Override
    @Transactional
    public void eliminar(Long id) {
        Inscripcion inscripcion = inscripcionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException(
                "Inscripción no encontrada con ID: " + id));

        // Intentar eliminar el PDF de S3 (error aquí no bloquea el DELETE en Oracle)
        if (inscripcion.getArchivoS3Key() != null) {
            cloudStorageService.eliminarPdf(inscripcion.getArchivoS3Key());
        }

        inscripcionRepository.deleteById(id);
        log.info("Inscripción [{}] eliminada de Oracle Cloud", id);
    }

    // ── Métodos privados ──────────────────────────────────────────────────

    /**
     * Valida que todos los IDs de cursos solicitados existen en la base de datos.
     *
     * @param encontrados lista de cursos recuperados desde Oracle Cloud
     * @param solicitados lista de IDs solicitados por el mensaje
     * @throws IllegalArgumentException si algún ID no fue encontrado
     */
    private void validarCursosEncontrados(List<Curso> encontrados, List<Long> solicitados) {
        if (encontrados.size() != solicitados.size()) {
            List<Long> idsEncontrados = encontrados.stream()
                .map(Curso::getId)
                .toList();
            List<Long> idsFaltantes = solicitados.stream()
                .filter(id -> !idsEncontrados.contains(id))
                .toList();
            throw new IllegalArgumentException(
                "Cursos no encontrados en la base de datos: " + idsFaltantes);
        }
    }

    /**
     * Calcula el total sumando los precios de todos los cursos.
     * Usa {@link BigDecimal} para aritmética exacta sin errores de punto flotante.
     *
     * @param cursos lista de cursos con sus precios
     * @return suma total de precios
     */
    private BigDecimal calcularTotal(List<Curso> cursos) {
        return cursos.stream()
            .map(Curso::getPrecio)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Genera el comprobante de inscripción en formato PDF usando OpenPDF.
     *
     * <p>Estructura del documento:
     * <ul>
     *   <li>Encabezado con nombre de la plataforma y número de comprobante.</li>
     *   <li>Sección de datos del estudiante.</li>
     *   <li>Tabla de cursos inscritos con nombre y precio individual.</li>
     *   <li>Sección final destacada con el total a pagar.</li>
     *   <li>Pie de página con fecha y hora de emisión.</li>
     * </ul>
     *
     * @param inscripcion entidad persistida (con ID asignado por Oracle)
     * @param cursos      lista de cursos con nombres y precios
     * @param total       total calculado a pagar
     * @return array de bytes del PDF generado
     * @throws DocumentException si OpenPDF no puede construir el documento
     */
    private byte[] generarPdf(
            Inscripcion inscripcion,
            List<Curso> cursos,
            BigDecimal total) throws DocumentException {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 50, 50, 60, 60);
        PdfWriter.getInstance(documento, outputStream);
        documento.open();

        // ── Fuentes ───────────────────────────────────────────────────────
        Font fuenteTitulo    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   20, Color.WHITE);
        Font fuenteSubtitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   13, new Color(30, 70, 130));
        Font fuenteNormal    = FontFactory.getFont(FontFactory.HELVETICA,        11, Color.BLACK);
        Font fuenteNegrita   = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   11, Color.BLACK);
        Font fuenteTotal     = FontFactory.getFont(FontFactory.HELVETICA_BOLD,   14, Color.WHITE);
        Font fuentePie       = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY);

        // ── Encabezado ────────────────────────────────────────────────────
        PdfPTable encabezado = new PdfPTable(1);
        encabezado.setWidthPercentage(100);
        PdfPCell celdaEncabezado = new PdfPCell(
            new Phrase("PLATAFORMA DE GESTIÓN DE CURSOS EN LÍNEA", fuenteTitulo));
        celdaEncabezado.setBackgroundColor(new Color(30, 70, 130));
        celdaEncabezado.setBorder(Rectangle.NO_BORDER);
        celdaEncabezado.setPadding(15);
        celdaEncabezado.setHorizontalAlignment(Element.ALIGN_CENTER);
        encabezado.addCell(celdaEncabezado);
        documento.add(encabezado);

        documento.add(new Paragraph(" "));

        // Número de comprobante
        Paragraph numComprobante = new Paragraph(
            "COMPROBANTE DE INSCRIPCIÓN N° " + inscripcion.getId(), fuenteSubtitulo);
        numComprobante.setAlignment(Element.ALIGN_CENTER);
        documento.add(numComprobante);

        documento.add(new Paragraph(" "));

        // ── Sección: Datos del Estudiante ─────────────────────────────────
        agregarSeparador(documento, "DATOS DEL ESTUDIANTE", fuenteSubtitulo);

        PdfPTable tablaDatos = new PdfPTable(2);
        tablaDatos.setWidthPercentage(100);
        tablaDatos.setWidths(new float[]{35f, 65f});

        agregarFilaDatos(tablaDatos, "ID Estudiante:",    inscripcion.getEstudianteId(),    fuenteNegrita, fuenteNormal);
        agregarFilaDatos(tablaDatos, "Nombre:",           inscripcion.getNombreEstudiante(), fuenteNegrita, fuenteNormal);
        agregarFilaDatos(tablaDatos, "Correo electrónico:", inscripcion.getEmailEstudiante(), fuenteNegrita, fuenteNormal);
        agregarFilaDatos(tablaDatos, "Fecha de inscripción:",
            inscripcion.getFechaInscripcion() != null
                ? inscripcion.getFechaInscripcion().format(FORMATO_FECHA)
                : LocalDateTime.now().format(FORMATO_FECHA),
            fuenteNegrita, fuenteNormal);
        documento.add(tablaDatos);

        documento.add(new Paragraph(" "));

        // ── Sección: Tabla de Cursos ──────────────────────────────────────
        agregarSeparador(documento, "CURSOS INSCRITOS", fuenteSubtitulo);

        PdfPTable tablaCursos = new PdfPTable(3);
        tablaCursos.setWidthPercentage(100);
        tablaCursos.setWidths(new float[]{10f, 65f, 25f});

        // Cabecera de la tabla
        agregarCabeceraCursos(tablaCursos, fuenteNegrita);

        // Filas de cursos
        int numero = 1;
        for (Curso curso : cursos) {
            Color colorFila = (numero % 2 == 0) ? new Color(240, 245, 255) : Color.WHITE;
            agregarFilaCurso(tablaCursos, numero, curso, fuenteNormal, colorFila);
            numero++;
        }
        documento.add(tablaCursos);

        documento.add(new Paragraph(" "));

        // ── Sección: Total a Pagar ────────────────────────────────────────
        PdfPTable tablaTotal = new PdfPTable(2);
        tablaTotal.setWidthPercentage(60);
        tablaTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tablaTotal.setWidths(new float[]{50f, 50f});

        PdfPCell celdaLabelTotal = new PdfPCell(new Phrase("TOTAL A PAGAR", fuenteTotal));
        celdaLabelTotal.setBackgroundColor(new Color(30, 70, 130));
        celdaLabelTotal.setBorderColor(new Color(20, 50, 100));
        celdaLabelTotal.setPadding(12);
        celdaLabelTotal.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell celdaValorTotal = new PdfPCell(
            new Phrase("$ " + formatearMonto(total), fuenteTotal));
        celdaValorTotal.setBackgroundColor(new Color(0, 100, 0));
        celdaValorTotal.setBorderColor(new Color(0, 80, 0));
        celdaValorTotal.setPadding(12);
        celdaValorTotal.setHorizontalAlignment(Element.ALIGN_CENTER);

        tablaTotal.addCell(celdaLabelTotal);
        tablaTotal.addCell(celdaValorTotal);
        documento.add(tablaTotal);

        documento.add(new Paragraph(" "));
        documento.add(new Paragraph(" "));

        // ── Pie de página ─────────────────────────────────────────────────
        Paragraph pie = new Paragraph(
            "Documento generado el " + LocalDateTime.now().format(FORMATO_FECHA)
            + "  |  Plataforma de Gestión de Cursos en Línea  |  Comprobante N° "
            + inscripcion.getId(),
            fuentePie);
        pie.setAlignment(Element.ALIGN_CENTER);
        documento.add(pie);

        documento.close();
        return outputStream.toByteArray();
    }

    /**
     * Agrega una fila separadora con título de sección al documento PDF.
     */
    private void agregarSeparador(Document doc, String titulo, Font fuente)
            throws DocumentException {
        PdfPTable separador = new PdfPTable(1);
        separador.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell(new Phrase(titulo, fuente));
        celda.setBackgroundColor(new Color(220, 230, 245));
        celda.setBorderColor(new Color(30, 70, 130));
        celda.setBorderWidth(1);
        celda.setPadding(7);
        separador.addCell(celda);
        doc.add(separador);
        doc.add(new Paragraph(" "));
    }

    /**
     * Agrega una fila de dos columnas (etiqueta + valor) a una tabla de datos.
     */
    private void agregarFilaDatos(PdfPTable tabla, String etiqueta, String valor,
            Font fuenteEtiqueta, Font fuenteValor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, fuenteEtiqueta));
        celdaEtiqueta.setBorder(Rectangle.BOTTOM);
        celdaEtiqueta.setBorderColor(new Color(200, 210, 230));
        celdaEtiqueta.setPadding(6);

        PdfPCell celdaValor = new PdfPCell(new Phrase(valor != null ? valor : "-", fuenteValor));
        celdaValor.setBorder(Rectangle.BOTTOM);
        celdaValor.setBorderColor(new Color(200, 210, 230));
        celdaValor.setPadding(6);

        tabla.addCell(celdaEtiqueta);
        tabla.addCell(celdaValor);
    }

    /**
     * Agrega la fila de cabecera a la tabla de cursos del PDF.
     */
    private void agregarCabeceraCursos(PdfPTable tabla, Font fuente) {
        Color colorCabecera = new Color(30, 70, 130);
        Font fuenteCabecera = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

        for (String titulo : new String[]{"N°", "Nombre del Curso", "Precio (CLP)"}) {
            PdfPCell celda = new PdfPCell(new Phrase(titulo, fuenteCabecera));
            celda.setBackgroundColor(colorCabecera);
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPadding(8);
            celda.setHorizontalAlignment(titulo.equals("Precio (CLP)")
                ? Element.ALIGN_RIGHT : Element.ALIGN_CENTER);
            tabla.addCell(celda);
        }
    }

    /**
     * Agrega una fila de curso a la tabla del PDF.
     */
    private void agregarFilaCurso(PdfPTable tabla, int numero, Curso curso,
            Font fuente, Color colorFondo) {
        Font fuentePrecio = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(0, 100, 0));

        PdfPCell celdaNum = new PdfPCell(new Phrase(String.valueOf(numero), fuente));
        celdaNum.setBackgroundColor(colorFondo);
        celdaNum.setBorderColor(new Color(200, 210, 230));
        celdaNum.setPadding(7);
        celdaNum.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell celdaNombre = new PdfPCell(new Phrase(curso.getNombre(), fuente));
        celdaNombre.setBackgroundColor(colorFondo);
        celdaNombre.setBorderColor(new Color(200, 210, 230));
        celdaNombre.setPadding(7);

        PdfPCell celdaPrecio = new PdfPCell(
            new Phrase("$ " + formatearMonto(curso.getPrecio()), fuentePrecio));
        celdaPrecio.setBackgroundColor(colorFondo);
        celdaPrecio.setBorderColor(new Color(200, 210, 230));
        celdaPrecio.setPadding(7);
        celdaPrecio.setHorizontalAlignment(Element.ALIGN_RIGHT);

        tabla.addCell(celdaNum);
        tabla.addCell(celdaNombre);
        tabla.addCell(celdaPrecio);
    }

    /**
     * Formatea un {@link BigDecimal} como número con separadores de miles.
     * Ejemplo: 129000 → "129.000"
     */
    private String formatearMonto(BigDecimal monto) {
        if (monto == null) return "0";
        java.text.NumberFormat formato = java.text.NumberFormat.getNumberInstance(
            new java.util.Locale("es", "CL"));
        formato.setMaximumFractionDigits(2);
        formato.setMinimumFractionDigits(0);
        return formato.format(monto);
    }

    /**
     * Último recurso: persiste una inscripción con datos mínimos y estado {@code ERROR}
     * cuando el fallo ocurrió antes del primer {@code save()} exitoso.
     *
     * @param mensaje datos del mensaje original de RabbitMQ
     */
    private void persistirConError(InscripcionMensaje mensaje) {
        try {
            Inscripcion errorInscripcion = new Inscripcion(
                mensaje.estudianteId(),
                mensaje.nombreEstudiante(),
                mensaje.emailEstudiante(),
                BigDecimal.ZERO,
                mensaje.cursosIds(),
                EstadoInscripcion.ERROR
            );
            inscripcionRepository.save(errorInscripcion);
            log.warn("Inscripción de error persistida para estudianteId: [{}]",
                mensaje.estudianteId());
        } catch (Exception ex) {
            log.error("No se pudo persistir la inscripción de error para estudianteId: [{}] — {}",
                mensaje.estudianteId(), ex.getMessage());
        }
    }
}
