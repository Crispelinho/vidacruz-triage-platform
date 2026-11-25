# syntax=docker/dockerfile:1

# Build stage: compile the Spring Boot executable jar with the Gradle wrapper (Gradle 8.14.8, JDK 21)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy build descriptors first so dependency resolution is cached between source changes
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && mv build/libs/*-SNAPSHOT.jar /workspace/app.jar

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
