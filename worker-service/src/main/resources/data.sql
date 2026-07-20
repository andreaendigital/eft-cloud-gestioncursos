-- ============================================================
-- data.sql — Datos iniciales para H2 en memoria
-- Se ejecuta automáticamente al arrancar el Worker Service.
-- Inserta el catálogo de cursos de prueba.
-- ============================================================

INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES ('Fundamentos de Cloud Computing', 89000, 'Introducción a IaaS, PaaS, SaaS y modelos de despliegue.', 1);
INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES ('Arquitectura de Microservicios con Spring Boot', 129000, 'Diseño e implementación con Spring Boot 3.x y Docker.', 1);
INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES ('Mensajería Asíncrona con RabbitMQ', 75000, 'Patrones de mensajería, exchanges y colas con Spring AMQP.', 1);
INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES ('AWS para Desarrolladores', 149000, 'EC2, S3, RDS, ECS y despliegue en la nube.', 1);
INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES ('Seguridad en APIs REST con Spring Security', 99000, 'OAuth2, JWT, Azure AD y Resource Server en Spring Boot.', 1);
INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES ('Java 17: Características Modernas', 59000, 'Records, sealed classes, pattern matching y text blocks.', 1);
INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES ('Persistencia con JPA y Oracle Cloud', 89000, 'Spring Data JPA, Hibernate y optimización de consultas.', 1);
INSERT INTO CURSOS (NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES ('CI/CD con GitHub Actions y Docker', 69000, 'Pipelines de CI/CD, imágenes Docker y deploy en la nube.', 1);
