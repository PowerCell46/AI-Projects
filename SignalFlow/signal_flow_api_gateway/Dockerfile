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

RUN useradd --system --create-home --shell /usr/sbin/nologin app

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

RUN chown app:app app.jar

USER app

EXPOSE 8080

# MaxRAMPercentage: the JVM caps max heap at ~25% of container memory by default, leaving most of
# the allocation unused. UseContainerSupport is not set -- it has been on by default since JDK 10.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]


# --- --- Run project --- ---

# docker build -t signal-flow-api-gateway .

# docker run -d --name signal-flow-api-gateway -p 8080:8080 -e DATASOURCE_URL=___ -e DATASOURCE_USERNAME=___ -e DATASOURCE_PASSWORD=___ -e JWT_SECRET=___ signal-flow-api-gateway
