# syntax=docker/dockerfile:1
# Imagen unica de los servicios Java (multi-etapa). Cada contenedor arranca su JAR:
#   command: ["java", "-jar", "/app/<servicio>.jar"]
# Requisito: solo Docker (no hace falta Java ni Maven en el equipo).

# ---------- Etapa 1: compilacion del reactor completo ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
# La cache de ~/.m2 acelera las compilaciones siguientes.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -ntp -DskipTests package \
 && mkdir -p /jars \
 && for m in gateway auth-service partners-service documents-service alerts-service as2-adapter; do \
      cp "$m"/target/"$m"-*.jar /jars/"$m".jar; \
    done

# ---------- Etapa 2: ejecucion ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
# Usuario sin privilegios; los directorios de datos le pertenecen.
RUN addgroup -S app && adduser -S -G app app \
 && mkdir -p /opt/as2_data \
 && chown -R app:app /app /opt/as2_data
COPY --from=build --chown=app:app /jars/*.jar /app/
# La JVM respeta el limite de memoria del contenedor.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
USER app
CMD ["java", "-jar", "/app/gateway.jar"]
