FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# Build with the committed Maven wrapper so image builds and local builds use the same Maven.
# chmod guards checkouts that lost the exec bit.
COPY .mvn/ .mvn/

COPY mvnw pom.xml ./

RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre

# wget is needed for the docker compose healthcheck (the base JRE image ships without wget/curl)
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin app

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

RUN chown app:app app.jar

USER app

EXPOSE 8083

# MaxRAMPercentage: the JVM caps max heap at ~25% of container memory by default, leaving most of
# the allocation unused. 65% (not more) leaves room for metaspace, thread stacks and network buffers, so a
# real shortage is a Java OutOfMemoryError in the log and not a silent kill by the container limit.
# ExitOnOutOfMemoryError: a JVM that survived an OOM is half broken; exiting lets the restart policy bring it back.
# UseContainerSupport is not set -- it has been on by default since JDK 10.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
