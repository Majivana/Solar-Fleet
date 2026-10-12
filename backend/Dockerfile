# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# BuildKit cache keeps downloaded dependencies between builds. Tests run in CI (./mvnw verify), not here.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && cp target/solar-fleet-mvp-0.1.0.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home --uid 10001 appuser
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
COPY --chmod=755 docker/healthcheck.sh /app/healthcheck.sh
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    TZ=Africa/Johannesburg
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 CMD ["/app/healthcheck.sh"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
