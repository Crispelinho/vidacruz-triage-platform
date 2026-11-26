FROM openjdk:21-slim as builder
WORKDIR /build
COPY . .
RUN chmod +x gradlew && ./gradlew build -x test
FROM openjdk:21-slim
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
