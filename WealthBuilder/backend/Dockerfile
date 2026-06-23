# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot fat jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

RUN apt-get update \
 && apt-get install -y --no-install-recommends maven \
 && rm -rf /var/lib/apt/lists/*

# Resolve dependencies first so they are cached as long as pom.xml is unchanged.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests


# ---- Runtime stage: slim JRE that runs the jar as a non-root user ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends wget \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system spring \
 && useradd --system --gid spring spring

COPY --from=build /build/target/backend-*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
