FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# Build with the committed Maven wrapper so image builds and local builds use the same Maven.
# chmod guards checkouts that lost the exec bit. The context is the Twitter root: the service
# builds together with the shared module it depends on.
COPY .mvn/ .mvn/

COPY mvnw pom.xml ./

RUN chmod +x mvnw

# Download dependencies (cached unless a pom changes)
COPY twitter_shared/pom.xml twitter_shared/pom.xml

COPY twitter_api_gateway/pom.xml twitter_api_gateway/pom.xml

RUN ./mvnw -pl twitter_api_gateway -am dependency:go-offline -B

# Copy source code
COPY twitter_shared/src twitter_shared/src

COPY twitter_api_gateway/src twitter_api_gateway/src

# Build application
RUN ./mvnw -pl twitter_api_gateway -am package -DskipTests -B

FROM eclipse-temurin:25-jre

# wget is needed for the docker compose healthcheck (the base JRE image ships without wget/curl)
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin app

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/twitter_api_gateway/target/*.jar app.jar

RUN chown app:app app.jar

USER app

EXPOSE 8080

# MaxRAMPercentage: the JVM caps max heap at ~25% of container memory by default, leaving most of
# the allocation unused. 65% (not more) leaves room for metaspace, thread stacks and network buffers, so a
# real shortage is a Java OutOfMemoryError in the log and not a silent kill by the container limit.
# ExitOnOutOfMemoryError: a JVM that survived an OOM is half broken; exiting lets the restart policy bring it back.
# UseContainerSupport is not set -- it has been on by default since JDK 10.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
