# Build stage: compiles the boot jar with the module's own Gradle wrapper, so the image does not depend on a jar
# built outside Docker. Build context is triage-service/triage-service.
FROM eclipse-temurin:21.0.13.1_2-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,id=triage-service-gradle,target=/root/.gradle sh -c "sed -i 's/\r$//' ./gradlew && sh ./gradlew bootJar --no-daemon"

# Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21.0.13.1_2-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/libs/*.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health/readiness" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
