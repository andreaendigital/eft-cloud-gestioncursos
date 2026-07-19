# ============================================================
# Dockerfile — BFF Service
# Build multi-stage para Java 17 + Spring Boot 3.x
# Usa Maven del sistema (sin wrapper mvnw)
# ============================================================

# ── Etapa 1: Compilación ─────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Instalar Maven en la imagen Alpine
RUN apk add --no-cache maven

# Copiar pom.xml primero para cachear la descarga de dependencias
COPY pom.xml .

# Descargar dependencias en capa separada
RUN mvn dependency:go-offline -B

# Copiar el código fuente y compilar el JAR ejecutable
COPY src/ src/
RUN mvn package -DskipTests -B

# ── Etapa 2: Imagen de ejecución ─────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL maintainer="equipo-cursos@duoc.cl"
LABEL service="bff-service"
LABEL version="1.0.0"

WORKDIR /app

# Usuario no-root para mayor seguridad en producción
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar solo el JAR generado desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE="prod"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
