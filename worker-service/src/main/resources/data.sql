-- ============================================================
-- data.sql — Datos iniciales para H2 en memoria
-- IDs explícitos para evitar "NULL not allowed for column ID"
-- Se ejecuta después de que Hibernate crea las tablas
-- (defer-datasource-initialization=true)
-- ============================================================

INSERT INTO CURSOS (ID, NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES (1, 'Fundamentos de Cloud Computing', 89000, 'Introducción a IaaS, PaaS, SaaS y modelos de despliegue.', 1);
INSERT INTO CURSOS (ID, NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES (2, 'Arquitectura de Microservicios con Spring Boot', 129000, 'Diseño e implementación de microservicios.', 1);
INSERT INTO CURSOS (ID, NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES (3, 'Mensajería Asíncrona con RabbitMQ', 75000, 'Patrones Pub/Sub y colas de mensajes.', 1);
INSERT INTO CURSOS (ID, NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES (4, 'AWS para Desarrolladores', 149000, 'S3, EC2, IAM y servicios en la nube.', 1);
INSERT INTO CURSOS (ID, NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES (5, 'Seguridad en APIs REST con Spring Security', 99000, 'OAuth2, JWT y protección de endpoints.', 1);
INSERT INTO CURSOS (ID, NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES (6, 'Java 17: Características Modernas', 59000, 'Records, Sealed Classes y Pattern Matching.', 1);
INSERT INTO CURSOS (ID, NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES (7, 'Persistencia con JPA y Oracle Cloud', 89000, 'Mapeo objeto-relacional y bases de datos.', 1);
INSERT INTO CURSOS (ID, NOMBRE, PRECIO, DESCRIPCION, ACTIVO) VALUES (8, 'CI/CD con GitHub Actions y Docker', 69000, 'Pipelines de integración y despliegue continuo.', 1);
