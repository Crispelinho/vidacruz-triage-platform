# OpenSpec Change 1: Platform Foundation — Detailed Specification

**Change ID:** `platform-foundation`  
**Component:** Both services  
**Date:** 2026-11-25

---

## ⚠️ PRODUCTION RESTRICTION

**This specification is authorized for development, evaluation, and non-production integration only.**

**Production deployments require ONE of these conditions:**
1. **VMware Spring Support contract** with current security patch commitment for Spring Boot 3.5.x, OR
2. **Migration to Spring Boot 4.1.x or later** (open-source support; planned post-delivery)

For production use, refer to ADR-10 for support path options.

---

## 1. Build Configuration

### Gradle (Root `build.gradle.kts`)

```gradle
plugins {
    id("java")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

subprojects {
    plugins.apply("java")
    
    repositories {
        mavenCentral()
    }

    dependencies {
        // Test dependencies
        testImplementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.0.3"))
        testImplementation("org.testcontainers:testcontainers:1.19.8")
        testImplementation("org.testcontainers:postgresql:1.19.8")
        testImplementation("org.testcontainers:kafka:1.19.8")
    }
}
```

### Triage Service `build.gradle.kts`

```gradle
plugins {
    id("java")
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.6"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.3")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")
    implementation("org.flywaydb:flyway-core:9.22.3")
    implementation("org.flywaydb:flyway-database-postgresql:9.22.3")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:testcontainers:1.19.8")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("org.testcontainers:kafka:1.19.8")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-XX:+AllowRedefinitionToAddDeleteMethods")
}
```

### Clinical Assistant Service `build.gradle.kts`

Same as Triage, plus:

```gradle
dependencies {
    implementation("org.springframework.ai:spring-ai-spring-boot-starter:1.1.1")
    implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter:1.1.1")
    implementation("org.postgresql:postgresql:42.7.1") // JDBC for pgvector starter
    implementation("org.springframework.data:spring-data-redis")
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
}
```

---

## 2. Database Configuration

### PostgreSQL Schema (Flyway `V1__init.sql`)

**Triage Database:**

```sql
-- Audit table (append-only, no UPDATE/DELETE allowed)
CREATE TABLE AuditoriaClinica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pacienteId UUID NOT NULL,
    estadoAnterior VARCHAR(50),
    estadoNuevo VARCHAR(50),
    tipoEvento VARCHAR(50) NOT NULL
);

-- Index for queries
CREATE INDEX idx_auditoria_paciente_timestamp 
    ON AuditoriaClinica (pacienteId, timestamp);

-- Revoke UPDATE and DELETE
REVOKE UPDATE, DELETE ON AuditoriaClinica FROM postgres;
GRANT SELECT, INSERT ON AuditoriaClinica TO postgres;

-- Outbox table (for change 2)
CREATE TABLE OutboxEvento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    eventId UUID NOT NULL UNIQUE,
    eventType VARCHAR(100) NOT NULL,
    pacienteId UUID,
    payload JSONB NOT NULL,
    publicadoEn TIMESTAMP WITH TIME ZONE,
    criadoEn TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_outbox_no_publicado 
    ON OutboxEvento (publicadoEn) 
    WHERE publicadoEn IS NULL;

-- Idempotency table (for change 3)
CREATE TABLE IdempotencyKey (
    key VARCHAR(255) PRIMARY KEY,
    requestHash VARCHAR(64) NOT NULL,
    pacienteId UUID,
    response JSONB NOT NULL,
    criadoEn TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_expira 
    ON IdempotencyKey (expira) 
    WHERE expira > CURRENT_TIMESTAMP;
```

**Assistant Database:**

```sql
-- Document metadata
CREATE TABLE DocumentoClinico (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocolo VARCHAR(100) NOT NULL,
    seccion VARCHAR(255) NOT NULL,
    fuente VARCHAR(255),
    criadoEn TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(protocolo, seccion)
);

-- Fragments with embeddings
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE FragmentoDocumental (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documentoId UUID NOT NULL REFERENCES DocumentoClinico(id),
    contenido TEXT NOT NULL,
    embedding vector(1536), -- OpenAI embedding dimension
    criadoEn TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(documentoId) REFERENCES DocumentoClinico(id)
);

CREATE INDEX idx_fragmento_embedding 
    ON FragmentoDocumental USING ivfflat (embedding vector_cosine_ops) 
    WITH (lists = 100);
```

---

## 3. Docker Compose

**`docker-compose.yml`**

```yaml
version: '3.8'

services:
  triage-service:
    build:
      context: ./triage-service
      dockerfile: Dockerfile
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: r2dbc:postgresql://triage-db:5432/vidacruz_triage
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
      TRIAGE_API_KEY: test-triage-key
    depends_on:
      triage-db:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: [ "CMD", "curl", "-f", "http://localhost:8081/health/ready" ]
      interval: 10s
      timeout: 5s
      retries: 5

  clinical-assistant-service:
    build:
      context: ./clinical-assistant-service
      dockerfile: Dockerfile
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: r2dbc:postgresql://assistant-db:5432/vidacruz_assistant
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
      ASSISTANT_API_KEY: test-assistant-key
      SPRING_AI_PROVIDER: ollama
      OLLAMA_BASE_URL: http://ollama:11434
    depends_on:
      assistant-db:
        condition: service_healthy
      kafka:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: [ "CMD", "curl", "-f", "http://localhost:8082/health/ready" ]
      interval: 10s
      timeout: 5s
      retries: 5

  triage-db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: vidacruz_triage
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - triage-db-data:/var/lib/postgresql/data
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U postgres" ]
      interval: 10s
      timeout: 5s
      retries: 5

  assistant-db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: vidacruz_assistant
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5433:5432"
    volumes:
      - assistant-db-data:/var/lib/postgresql/data
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U postgres" ]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_LOG_RETENTION_MS: 2592000000  # 30 days
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    ports:
      - "29092:29092"
    depends_on:
      - zookeeper
    healthcheck:
      test: [ "CMD-SHELL", "kafka-topics --bootstrap-server localhost:9092 --list" ]
      interval: 10s
      timeout: 5s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"
    healthcheck:
      test: [ "CMD-SHELL", "echo ruok | nc localhost 2181" ]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: [ "CMD-SHELL", "redis-cli ping" ]
      interval: 10s
      timeout: 5s
      retries: 5

  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama
    # Note: Pull a model manually with: docker exec ollama ollama pull llama2
    # For testing without models, the service is optional

  alert-receiver:
    image: mock-alert-receiver:latest
    build:
      context: ./test-support/alert-receiver
      dockerfile: Dockerfile
    ports:
      - "8090:8080"
    environment:
      LOG_LEVEL: INFO

volumes:
  triage-db-data:
  assistant-db-data:
  ollama-data:
```

---

## 4. GitHub Actions Workflow

**`.github/workflows/ci.yml`**

```yaml
name: CI

on:
  pull_request:
    branches: [ develop ]
  push:
    branches: [ develop ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Triage service tests
        run: cd triage-service && ./gradlew build

      - name: Run Assistant service tests
        run: cd clinical-assistant-service && ./gradlew build

      - name: Start docker-compose
        run: docker-compose up -d

      - name: Wait for services
        run: sleep 30 && curl -f http://localhost:8081/health/ready && curl -f http://localhost:8082/health/ready

      - name: Run integration tests
        run: |
          cd triage-service && ./gradlew integrationTest
          cd ../clinical-assistant-service && ./gradlew integrationTest

      - name: Logs on failure
        if: failure()
        run: docker-compose logs
```

---

## 5. Dockerfile

**Both services have similar Dockerfiles:**

```dockerfile
FROM openjdk:21-slim as builder

WORKDIR /app
COPY . .
RUN ./gradlew build --no-daemon

FROM openjdk:21-slim

COPY --from=builder /app/build/libs/*.jar app.jar

ENTRYPOINT [ "java", "-XX:+UseG1GC", "-Xmx512m", "-Xms256m", "-jar", "app.jar" ]
```

---

## 6. Application Properties

**`triage-service/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: triage-service
  webflux:
    base-path: /api
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/vidacruz_triage
    username: postgres
    password: postgres
    pool:
      max-idle-time: 30m
      validation-query: SELECT 1
  datasource:
    url: jdbc:postgresql://localhost:5432/vidacruz_triage
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:/db/migration
  kafka:
    bootstrap-servers: localhost:29092
    producer:
      acks: all
    consumer:
      group-id: triage-service
  jpa:
    database: postgresql
    hibernate:
      ddl-auto: validate

server:
  port: 8081
  shutdown: graceful

management:
  endpoints:
    web:
      exposure:
        include: health,readiness,liveness
  endpoint:
    health:
      show-details: when-authorized
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true

triage:
  api-key: ${TRIAGE_API_KEY:test-triage-key}
  sla:
    evaluation-interval-seconds: 30
    defaults:
      CRITICO: 5
      URGENTE: 10
      URGENTE_2: 30
      NO_URGENTE: 60
      NO_URGENTE_2: 120
```

**`clinical-assistant-service/src/main/resources/application.yml`** — Similar, plus:

```yaml
spring:
  ai:
    provider: ${SPRING_AI_PROVIDER:OLLAMA}
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model: llama2
  redis:
    host: ${SPRING_REDIS_HOST:localhost}
    port: ${SPRING_REDIS_PORT:6379}

assistant:
  api-key: ${ASSISTANT_API_KEY:test-assistant-key}
```

---

## 7. Test Strategy

### Unit Tests
- Minimal; focus on configuration loading and bean creation.

### Integration Tests
- Test PostgreSQL connectivity (Testcontainers).
- Test Kafka producer/consumer (Testcontainers).
- Test audit table constraints (no UPDATE/DELETE).
- Test BlockHound for reactive compliance.

---

## 8. Acceptance Test Cases

| ID | Test | Expected Result |
|---|---|---|
| TST-PF-01 | `./gradlew build` from each service | Build succeeds, no warnings about Boot 3.5. |
| TST-PF-02 | `docker-compose up` | All services reach `healthy` within 30s. |
| TST-PF-03 | `curl http://localhost:8081/health/ready` | HTTP 200 `{ "status": "UP" }`. |
| TST-PF-04 | `curl http://localhost:8082/health/ready` | HTTP 200 `{ "status": "UP" }`. |
| TST-PF-05 | Integration test: audit table UPDATE | Fails with RBAC error. |
| TST-PF-06 | Integration test: audit table DELETE | Fails with RBAC error. |
| TST-PF-07 | GitHub Actions workflow | Passes build and unit tests on PR. |

