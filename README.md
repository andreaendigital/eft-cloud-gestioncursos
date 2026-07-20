# Evaluación Final Transversal Cloud Native (EFT)

Alumna : Andrea Rosero
Link a informe : [Ver informe](https://docs.google.com/document/d/1XpDYf44h45zYQFODyzZj7a-EGdVElQIo/edit?usp=sharing&ouid=116934276410792938897&rtpof=true&sd=true)
Link a video : 

## Plataforma de Gestión de Cursos en Línea

Sistema de inscripción a cursos en línea basado en arquitectura de microservicios. Permite a los estudiantes inscribirse en cursos de forma asíncrona, generando un comprobante PDF almacenado en AWS S3.



---

## Arquitectura

```
Cliente / API Gateway
        │
        ▼
┌─────────────────────┐
│   BFF Service :8080 │  ← Valida JWT (Azure AD) · Produce mensajes
└────────┬────────────┘
         │  RabbitMQ (cola-inscripciones)
         ▼
┌─────────────────────────┐
│  Worker Service :8081   │  ← Consume mensajes · Calcula total
│  H2 en memoria          │    Genera PDF (OpenPDF) · Sube a S3
└─────────────────────────┘
```

## Stack Tecnológico

| Componente | Tecnología |
|---|---|
| Backend | Spring Boot 3.2 · Java 17 |
| Mensajería | RabbitMQ (Docker) · Spring AMQP |
| Base de datos | H2 en memoria (modo Oracle) |
| Almacenamiento | AWS S3 · AWS SDK v2 |
| Autenticación | Azure AD (OAuth2 JWT) · Spring Security |
| CI/CD | GitHub Actions · Docker Hub · AWS EC2 |

---

## Estructura del Proyecto

```
gestion-cursos-en-linea/
├── bff-service/          # Backend for Frontend (puerto 8080)
├── worker-service/       # Procesador asíncrono (puerto 8081)
├── scripts/schema.sql    # DDL Oracle Cloud (referencia)
├── docker-compose.yml    # Desarrollo local (RabbitMQ)
├── docker-compose.prod.yml # Producción en EC2
└── .github/workflows/cicd.yml
```

---

## Ejecución Local

**Requisitos:** Java 17, Maven, Docker

**1. Levantar RabbitMQ:**
```bash
docker-compose up -d
```

**2. Iniciar Worker Service:**
```bash
cd worker-service
mvn spring-boot:run
```

**3. Iniciar BFF Service** (requiere variables de entorno Azure AD):
```bash
cd bff-service
export AZURE_AD_ISSUER_URI=https://login.microsoftonline.com/{tenant-id}/v2.0
export AZURE_AD_JWKS_URI=https://login.microsoftonline.com/{tenant-id}/discovery/v2.0/keys
export AZURE_CLIENT_ID={client-id}
mvn spring-boot:run
```

---

## Endpoints Principales

Todos los endpoints pasan por el BFF en el puerto **8080**.

### Cursos
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/cursos` | Lista todos los cursos |
| GET | `/api/v1/cursos/{id}` | Obtiene un curso por ID |
| POST | `/api/v1/cursos` | Crea un nuevo curso |
| PUT | `/api/v1/cursos/{id}` | Actualiza un curso |
| DELETE | `/api/v1/cursos/{id}` | Elimina un curso |

### Inscripciones
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/inscripciones` | Inscribe a un estudiante (asíncrono) |
| GET | `/api/v1/inscripciones/{id}` | Consulta una inscripción |
| GET | `/api/v1/inscripciones/{id}/descargar` | Descarga el comprobante PDF |
| PUT | `/api/v1/inscripciones/{id}` | Actualiza una inscripción |
| DELETE | `/api/v1/inscripciones/{id}` | Elimina una inscripción |

### Payload de inscripción

```json
{
  "estudianteId": "1024",
  "nombreEstudiante": "Juan Pérez",
  "emailEstudiante": "juan.perez@email.com",
  "cursosIds": [1, 3, 5]
}
```

**Respuesta:** `HTTP 202 Accepted` — el Worker procesa de forma asíncrona.

---

## Cursos de Prueba (cargados automáticamente)

| ID | Nombre | Precio |
|---|---|---|
| 1 | Fundamentos de Cloud Computing | $89.000 |
| 2 | Arquitectura de Microservicios con Spring Boot | $129.000 |
| 3 | Mensajería Asíncrona con RabbitMQ | $75.000 |
| 4 | AWS para Desarrolladores | $149.000 |
| 5 | Seguridad en APIs REST con Spring Security | $99.000 |
| 6 | Java 17: Características Modernas | $59.000 |
| 7 | Persistencia con JPA y Oracle Cloud | $89.000 |
| 8 | CI/CD con GitHub Actions y Docker | $69.000 |

---

## Pipeline CI/CD

El pipeline se activa automáticamente en cada `push` a `main`:

```
push → main
  ├── 1. Build & Test    (mvn clean package)
  ├── 2. Docker Hub      (build + push bff-service y worker-service)
  └── 3. Deploy EC2      (SSH → docker pull + docker-compose up)
```

### Secrets requeridos en GitHub

| Secret | Descripción |
|---|---|
| `DOCKERHUB_USERNAME` | Usuario de Docker Hub |
| `DOCKERHUB_TOKEN` | Token de acceso Docker Hub |
| `EC2_HOST` | IP pública de la instancia EC2 |
| `EC2_USER` | Usuario SSH (`ec2-user` o `ubuntu`) |
| `EC2_SSH_KEY` | Clave privada PEM completa |
| `AZURE_AD_ISSUER_URI` | URI del emisor JWT de Azure AD |
| `AZURE_AD_JWKS_URI` | URI del endpoint JWKS de Azure AD |
| `AZURE_CLIENT_ID` | Application ID del BFF en Azure AD |
| `RABBITMQ_USERNAME` | Usuario RabbitMQ |
| `RABBITMQ_PASSWORD` | Contraseña RabbitMQ |
| `AWS_ACCESS_KEY_ID` | Credencial temporal AWS Academy |
| `AWS_SECRET_ACCESS_KEY` | Credencial temporal AWS Academy |
| `AWS_SESSION_TOKEN` | Token de sesión AWS Academy |
| `AWS_S3_REGION` | Región del bucket S3 |
| `AWS_S3_BUCKET_NAME` | Nombre del bucket S3 |

> **Nota AWS Academy:** las credenciales AWS expiran cada ~4 horas. Actualiza los tres secrets `AWS_*` al iniciar cada sesión de laboratorio.

---

## Flujo de Inscripción

```
1. Cliente envía POST /api/v1/inscripciones al BFF
2. BFF valida el JWT de Azure AD
3. BFF publica el mensaje en cola-inscripciones (RabbitMQ)
4. BFF responde HTTP 202 Accepted inmediatamente
5. Worker consume el mensaje de forma asíncrona
6. Worker consulta precios de los cursos en H2
7. Worker calcula el total a pagar
8. Worker genera el comprobante PDF (OpenPDF)
9. Worker sube el PDF a AWS S3
10. Worker persiste la Inscripcion con estado PROCESADO
```
