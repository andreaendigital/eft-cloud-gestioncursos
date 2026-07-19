# ============================================================
# Dockerfile — BFF Service
# CONTEXTO DE BUILD: raíz del proyecto (no bff-service/)
# El pipeline debe llamarlo con: --file bff-service/Dockerfile .
# (context = raíz del repo para que el POM padre sea accesible)
# ============================================================

# ── Etapa 1: Compilación ─────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

RUN apk add --no-cache maven

# Copiar POM raíz y POMs de módulos para cachear dependencias
COPY pom.xml .
COPY bff-service/pom.xml bff-service/pom.xml
COPY worker-service/pom.xml worker-service/pom.xml

# Descargar dependencias de todos los módulos en capa separada
RUN mvn dependency:go-offline -B -pl bff-service -am

# Copiar solo el código fuente del bff-service
COPY bff-service/src bff-service/src

# Compilar solo el módulo bff-service
RUN mvn package -DskipTests -B -pl bff-service -am

# ── Etapa 2: Imagen de ejecución ─────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL maintainer="equipo-cursos@duoc.cl"
LABEL service="bff-service"
LABEL version="1.0.0"

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/bff-service/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE="prod"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
