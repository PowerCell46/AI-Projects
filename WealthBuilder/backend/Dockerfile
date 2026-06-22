# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot fat jar with Maven + JDK 25 ----
# Pin to a specific Maven patch to make builds reproducible — the floating `3-…` tag silently
# picks up new major versions between builds.
FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /build


# Resolve dependencies first, in their own layer, so they are cached as long as pom.xml
# is unchanged — source edits then skip the (slow) dependency download.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Run the test suite as part of the image build so a regression fails the build, not production.
RUN mvn -B -q clean package


# ---- Runtime stage: a slim JRE that runs the jar as a non-root user ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app


# Create the non-root user. No extra packages are installed — the health probe below uses a
# single-file Java program backed by the JDK's own HttpURLConnection, avoiding a curl/wget apt
# install and the CVE surface that comes with it.
RUN groupadd --system spring \
 && useradd --system --gid spring spring

# `backend-*.jar` is scoped to the Maven artifact ID so it won't accidentally match a second
# jar if the build ever emits one (test jar, original pre-repackaged jar, etc.).
COPY --from=build /build/target/backend-*.jar app.jar

# Write the health-probe source once at image build time; `java Health.java` compiles and runs
# it in-process at check time using only classes already in the JDK — no apt install required.
RUN printf 'class Health {\n  public static void main(String[] a) throws Exception {\n    var c = (java.net.HttpURLConnection)\n        new java.net.URL("http://localhost:8080/actuator/health").openConnection();\n    c.setConnectTimeout(4_000);\n    c.setReadTimeout(4_000);\n    System.exit(c.getResponseCode() == 200 ? 0 : 1);\n  }\n}' > /app/Health.java \
 && chown spring:spring app.jar /app/Health.java
USER spring

EXPOSE 8080

# Actuator exposes only the health probe (see application.properties).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD java /app/Health.java

ENTRYPOINT ["java", "-jar", "app.jar"]
