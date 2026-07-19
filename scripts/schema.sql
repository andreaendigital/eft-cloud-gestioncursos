-- ============================================================
-- schema.sql — DDL completo para Oracle Cloud
-- Plataforma de Gestión de Cursos en Línea
--
-- Instrucciones de ejecución:
--   1. Conectarse a Oracle Cloud con SQL*Plus, SQL Developer
--      o cualquier cliente JDBC compatible con ojdbc11.
--   2. Ejecutar este script completo con un usuario que tenga
--      permisos CREATE TABLE, CREATE SEQUENCE.
--   3. Para poblar datos de prueba, ejecutar también
--      el bloque INSERT al final del script.
--
-- Tablas creadas:
--   - CURSOS           : catálogo de cursos disponibles
--   - INSCRIPCIONES    : registros de inscripción procesados
--   - INSCRIPCION_CURSOS: relación N:M inscripción ↔ cursos
-- ============================================================


-- ============================================================
-- 1. LIMPIEZA PREVIA (ejecutar solo en entornos de desarrollo)
--    Elimina objetos existentes para permitir recreación limpia.
-- ============================================================

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE INSCRIPCION_CURSOS CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE INSCRIPCIONES CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE CURSOS CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_CURSOS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE SEQ_INSCRIPCIONES';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/


-- ============================================================
-- 2. SECUENCIAS
--    Generan identificadores únicos para cada tabla principal.
-- ============================================================

-- Secuencia para la tabla CURSOS
CREATE SEQUENCE SEQ_CURSOS
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Secuencia para la tabla INSCRIPCIONES
CREATE SEQUENCE SEQ_INSCRIPCIONES
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;


-- ============================================================
-- 3. TABLA: CURSOS
--    Catálogo de cursos disponibles en la plataforma.
--    El Worker Service consulta esta tabla para obtener
--    los precios vigentes al procesar una inscripción.
-- ============================================================

CREATE TABLE CURSOS (
    ID          NUMBER          DEFAULT SEQ_CURSOS.NEXTVAL
                                CONSTRAINT PK_CURSOS PRIMARY KEY,
    NOMBRE      VARCHAR2(200)   NOT NULL,
    PRECIO      NUMBER(10, 2)   NOT NULL
                                CONSTRAINT CHK_CURSOS_PRECIO
                                CHECK (PRECIO >= 0),
    DESCRIPCION VARCHAR2(1000),
    ACTIVO      NUMBER(1)       DEFAULT 1 NOT NULL
                                CONSTRAINT CHK_CURSOS_ACTIVO
                                CHECK (ACTIVO IN (0, 1))
);

COMMENT ON TABLE  CURSOS             IS 'Catálogo de cursos disponibles en la plataforma';
COMMENT ON COLUMN CURSOS.ID          IS 'Identificador único generado por SEQ_CURSOS';
COMMENT ON COLUMN CURSOS.NOMBRE      IS 'Nombre descriptivo del curso';
COMMENT ON COLUMN CURSOS.PRECIO      IS 'Precio en pesos (CLP), debe ser >= 0';
COMMENT ON COLUMN CURSOS.DESCRIPCION IS 'Descripción detallada del contenido del curso';
COMMENT ON COLUMN CURSOS.ACTIVO      IS '1 = activo y disponible para inscripción, 0 = inactivo';


-- ============================================================
-- 4. TABLA: INSCRIPCIONES
--    Registra cada inscripción procesada por el Worker Service.
--    El campo ESTADO refleja el resultado del procesamiento
--    asíncrono: PROCESADO (exitoso) o ERROR (fallo).
-- ============================================================

CREATE TABLE INSCRIPCIONES (
    ID                  NUMBER          DEFAULT SEQ_INSCRIPCIONES.NEXTVAL
                                        CONSTRAINT PK_INSCRIPCIONES PRIMARY KEY,
    ESTUDIANTE_ID       VARCHAR2(100)   NOT NULL,
    NOMBRE_ESTUDIANTE   VARCHAR2(200)   NOT NULL,
    EMAIL_ESTUDIANTE    VARCHAR2(200)   NOT NULL
                                        CONSTRAINT CHK_INSC_EMAIL
                                        CHECK (EMAIL_ESTUDIANTE LIKE '%@%'),
    TOTAL_PAGADO        NUMBER(10, 2)   NOT NULL
                                        CONSTRAINT CHK_INSC_TOTAL
                                        CHECK (TOTAL_PAGADO >= 0),
    FECHA_INSCRIPCION   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ARCHIVO_S3_KEY      VARCHAR2(500),
    ESTADO              VARCHAR2(20)    NOT NULL
                                        CONSTRAINT CHK_INSC_ESTADO
                                        CHECK (ESTADO IN ('PROCESADO', 'ERROR'))
);

COMMENT ON TABLE  INSCRIPCIONES                  IS 'Registros de inscripción procesados por el Worker Service';
COMMENT ON COLUMN INSCRIPCIONES.ID               IS 'Identificador único generado por SEQ_INSCRIPCIONES';
COMMENT ON COLUMN INSCRIPCIONES.ESTUDIANTE_ID    IS 'Identificador del estudiante proveniente del payload del BFF';
COMMENT ON COLUMN INSCRIPCIONES.NOMBRE_ESTUDIANTE IS 'Nombre completo del estudiante';
COMMENT ON COLUMN INSCRIPCIONES.EMAIL_ESTUDIANTE IS 'Correo electrónico del estudiante';
COMMENT ON COLUMN INSCRIPCIONES.TOTAL_PAGADO     IS 'Suma de precios de todos los cursos inscritos';
COMMENT ON COLUMN INSCRIPCIONES.FECHA_INSCRIPCION IS 'Timestamp de cuando el Worker procesó la inscripción';
COMMENT ON COLUMN INSCRIPCIONES.ARCHIVO_S3_KEY   IS 'Clave S3 del comprobante PDF: resumen-{id}/comprobante.pdf';
COMMENT ON COLUMN INSCRIPCIONES.ESTADO           IS 'PROCESADO = completado exitosamente, ERROR = fallo en el procesamiento';

-- Índice para búsquedas frecuentes por estudiante
CREATE INDEX IDX_INSC_ESTUDIANTE_ID
    ON INSCRIPCIONES (ESTUDIANTE_ID);

-- Índice para filtrar por estado en reportes operacionales
CREATE INDEX IDX_INSC_ESTADO
    ON INSCRIPCIONES (ESTADO);


-- ============================================================
-- 5. TABLA: INSCRIPCION_CURSOS
--    Tabla intermedia (join table) que registra la relación
--    N:M entre inscripciones y los cursos incluidos.
--    Mapeada en JPA con @ElementCollection o @ManyToMany.
-- ============================================================

CREATE TABLE INSCRIPCION_CURSOS (
    INSCRIPCION_ID  NUMBER  NOT NULL,
    CURSO_ID        NUMBER  NOT NULL,
    CONSTRAINT PK_INSCRIPCION_CURSOS
        PRIMARY KEY (INSCRIPCION_ID, CURSO_ID),
    CONSTRAINT FK_INSC_CURSOS_INSCRIPCION
        FOREIGN KEY (INSCRIPCION_ID)
        REFERENCES INSCRIPCIONES (ID)
        ON DELETE CASCADE,
    CONSTRAINT FK_INSC_CURSOS_CURSO
        FOREIGN KEY (CURSO_ID)
        REFERENCES CURSOS (ID)
        ON DELETE RESTRICT
);

COMMENT ON TABLE  INSCRIPCION_CURSOS               IS 'Relación N:M entre inscripciones y cursos';
COMMENT ON COLUMN INSCRIPCION_CURSOS.INSCRIPCION_ID IS 'FK a INSCRIPCIONES.ID';
COMMENT ON COLUMN INSCRIPCION_CURSOS.CURSO_ID       IS 'FK a CURSOS.ID';


-- ============================================================
-- 6. DATOS DE PRUEBA
--    Cursos de ejemplo para validar el flujo completo.
--    Los IDs generados por SEQ_CURSOS serán 1..8.
-- ============================================================

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION) VALUES (
    'Fundamentos de Cloud Computing',
    89000,
    'Introducción a los conceptos de computación en la nube: IaaS, PaaS, SaaS y modelos de despliegue.'
);

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION) VALUES (
    'Arquitectura de Microservicios con Spring Boot',
    129000,
    'Diseño e implementación de microservicios usando Spring Boot 3.x, Docker y Kubernetes.'
);

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION) VALUES (
    'Mensajería Asíncrona con RabbitMQ',
    75000,
    'Patrones de mensajería, exchanges, colas y bindings. Integración con Spring AMQP.'
);

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION) VALUES (
    'AWS para Desarrolladores',
    149000,
    'Servicios principales de AWS: EC2, S3, RDS, ECS y despliegue de aplicaciones en la nube.'
);

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION) VALUES (
    'Seguridad en APIs REST con Spring Security',
    99000,
    'Autenticación OAuth2, JWT, Azure AD y configuración de Resource Server en Spring Boot.'
);

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION) VALUES (
    'Java 17: Características Modernas',
    59000,
    'Records, sealed classes, pattern matching, text blocks y mejoras de la JVM en Java 17.'
);

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION) VALUES (
    'Persistencia con JPA y Oracle Cloud',
    89000,
    'Spring Data JPA, Hibernate, secuencias Oracle, relaciones y optimización de consultas.'
);

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION) VALUES (
    'CI/CD con GitHub Actions y Docker',
    69000,
    'Pipelines de integración y entrega continua, construcción de imágenes Docker y deploy a la nube.'
);

COMMIT;
