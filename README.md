# VidaCruz Triage Platform

Event-driven triage management system built with Spring Boot 3.5.16, Kafka, PostgreSQL, and pgvector RAG.

## Quick Start

```bash
docker-compose up -d
./gradlew build -x test
```

## Architecture

- **Triage Service**: Patient registration, escalation (port 8081)
- **Clinical Assistant**: RAG-based clinical insights (port 8082)
- **Event Bridge**: Kafka (async, no sync HTTP coupling)
- **Storage**: PostgreSQL (per-service), Redis (projections), pgvector (embeddings)

## Technologies

- Java 21, Spring Boot 3.5.16 (evaluation only - requires Boot 4.1 or commercial support for production)
- Spring Cloud 2025.0.3, Spring Kafka, Spring Data R2DBC
- PostgreSQL 16 with pgvector extension
- Kafka 7.5.0, Redis 7.2

## Production Note

**This baseline is for development and evaluation only.** Boot 3.5.16 reached EOL on 2026-06-30. Production deployments require either:
1. Commercial Spring Support contract for Boot 3.5.x, OR
2. Migration to Boot 4.1.x+ (open-source support)

See ADR-10 for details and post-delivery migration plan.

## ADRs

- ADR-01: Two-service decomposition (Triage + Clinical Assistant)
- ADR-02: Event contract and retention strategy
- ADR-03: Transactional outbox with idempotent consumers
- ADR-10: Spring Boot 3.5.16 baseline (EOL documented)
- ADR-14: One database per service with Redis projections
