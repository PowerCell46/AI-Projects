# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot fat jar with Maven + JDK 25 ----
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /build


# Resolve dependencies first, in their own layer, so they are cached as long as pom.xml
# is unchanged — source edits then skip the (slow) dependency download.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests


# ---- Runtime stage: a slim JRE that runs the jar as a non-root user ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app


# curl backs the container HEALTHCHECK below; the dedicated user avoids running as root.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system spring \
 && useradd --system --gid spring spring

COPY --from=build /build/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

# Actuator exposes only the health probe (see application.properties).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
