# ============================================================
# Dockerfile — BFF Service
# Build multi-stage optimizado para Java 17 + Spring Boot 3.x
#
# Etapa 1 (build): compila el proyecto con Maven y genera el JAR.
# Etapa 2 (runtime): imagen mínima JRE para ejecutar el JAR.
#
# Uso:
#   docker build -t bff-service:latest .
#   docker run -p 8080:8080 \
#     -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=<uri> \
#     -e SPRING_RABBITMQ_HOST=<host> \
#     bff-service:latest
# ============================================================

# ── Etapa 1: Compilación ─────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

LABEL stage="builder"
WORKDIR /app

# Copiar descriptor de dependencias primero para aprovechar
# la caché de Docker: si pom.xml no cambia, no se re-descargan deps.
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

# Descargar dependencias en capa separada (mejora velocidad de rebuild)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copiar el código fuente y compilar el JAR ejecutable
COPY src/ src/
RUN ./mvnw package -DskipTests -B

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

# Puerto en el que el BFF Service escucha
EXPOSE 8080

# Variables de entorno con valores por defecto (se sobreescriben en runtime)
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE="prod"

# Punto de entrada: ejecutar el JAR con opciones JVM configurables
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
