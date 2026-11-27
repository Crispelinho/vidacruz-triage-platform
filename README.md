# VidaCruz Triage Platform — local demo

One command starts both services and their infrastructure on `localhost`. No Azure subscription, public URL, or LLM provider key is needed for RF-01/RF-02/RF-03/RF-05/RF-06; RF-04 needs a reachable provider or demonstrates the documented 503 fallback.

## Run it

Prerequisites: Docker with Compose v2. Nothing else is installed on the host.

```bash
cp .env.example .env          # optional: local overrides
docker compose up -d --build  # first build downloads Gradle deps and both JDK images
docker compose ps
```

| Service | URL | Auth |
|---|---|---|
| Triage API | `http://localhost:8081` | `X-API-Key: test-triage-key` |
| Clinical Assistant API | `http://localhost:8082` | `X-API-Key: test-assistant-key` |
| Health (public) | `http://localhost:8081/actuator/health`, `http://localhost:8082/actuator/health` | none |
| PostgreSQL (triage) | `localhost:5432` | `triage_app` / `triage_local` |
| PostgreSQL + pgvector (assistant) | `localhost:5433` | `assistant_app` / `assistant_local` |
| Kafka | `localhost:9092` | topic `triaje.eventos` |
| Redis | `localhost:6379` | urgency projection |
| Prometheus | `http://localhost:9090` | scraper sends the demo keys |
| Grafana | `http://localhost:3000` | anonymous viewer; local admin password `admin-local` |
| Jaeger | `http://localhost:16686` | local traces from both services |

Stop with `docker compose down`; keep data with `docker compose down` (volumes persist) or delete it with `docker compose down -v`.

## First request

```bash
curl -s http://localhost:8081/actuator/health
curl -s -i http://localhost:8081/triage/alertas \
  -H 'X-API-Key: wrong'          # 401, body {code, message, correlationId}
curl -s http://localhost:8081/triage/alertas \
  -H 'X-API-Key: test-triage-key'  # 200, []
```

Missing or wrong keys return `401` with `{"code":"UNAUTHORIZED","message":"Invalid or missing X-API-Key header","correlationId":"..."}`. Keys come from `TRIAGE_API_KEY` / `ASSISTANT_API_KEY`; the values above are localhost-only defaults. See `docs/adr/ADR-07-authentication-api-key.md`.

## Local verification checklist

Run in order. Details and ready-to-import requests are in [`docs/demo/local-verification.md`](docs/demo/local-verification.md) and [`postman/`](postman/).

- [ ] Both `/actuator/health` endpoints return `{"status":"UP"}`.
- [ ] `POST /triage/pacientes` with E1…E6 returns `201` and the expected `nivel` (`CRITICO`, `URGENTE`, `URGENTE_2`, `NO_URGENTE`, `NO_URGENTE_2`, `CRITICO`).
- [ ] `GET /triage/pacientes/{id}` and `PATCH /triage/pacientes/{id}/estado` with `{"estado":"EN_CONSULTA"}` then `{"estado":"ALTA"}`.
- [ ] RF-03: wait for the short demo SLA, then `GET /triage/alertas` shows a `PENDIENTE` alert; changing state resolves it.
- [ ] `POST /triage/alertas/{id}/reconocimiento` with `{"responsable":"..."}` moves it to `RECONOCIDA`; a second call returns `TRG-009`.
- [ ] `GET /triage/alertas/stream` pushes the initial snapshot and live changes (use `curl -N` or streaming `fetch`).
- [ ] `GET /triage/reportes/operacion?desde=...&hasta=...` with ISO 8601 offsets (`2026-01-01T00:00:00Z`).
- [ ] RF-04: `POST /rag` returns an answer with the fixed notice, or `503` with `LLM_UNAVAILABLE` when no provider is configured.
- [ ] `GET /asistente/estado-urgencias` reflects patients published through `triaje.eventos` (outbox publisher is enabled in Compose).
- [ ] NFR-07: `/actuator/prometheus` on both services needs `X-API-Key`, and Prometheus (`localhost:9090`) plus the provisioned Grafana dashboard (`localhost:3000`) show the counters after the requests above.
- [ ] NFR-16: a registration with a W3C `traceparent` header produces one trace across `triage-service` and `clinical-assistant-service` in Jaeger (`localhost:16686`).

## Known limits

- **Deployment artifacts.** The AKS manifests live in `triage-service/deployment/k8s/`, `clinical-assistant-service/deployment/k8s/` and `infra/k8s/`; the Azure IaC is in `infra/azure/`, and the CI/deploy workflows are in `.github/workflows/`. The validated target and public URL are recorded in [ADR-12](docs/adr/ADR-12-aks-deployment.md) and `docs/operations/despliegue-azure-y-pruebas.md`. The live URL depends on the `azure` GitHub environment; no credentials are stored here. Local traces still go to the Jaeger container.
- **RF-04 path drift.** API-04 documents `POST /asistente/consulta`, but the running scaffold exposes `POST /rag`. Use the implemented path until the endpoint is aligned.
- **RF-04 provider.** The default chain targets Ollama at `http://ollama:11434`; the `ollama` Compose profile does not auto-pull a model. Without a reachable provider, expect the fixed 503 notice.
- **SSE and browsers.** `EventSource` cannot send `X-API-Key`. Use streaming `fetch` with the header, `curl -N`, or a backend proxy (ADR-15).
- **No idempotency header.** RF-01 does not yet implement `Idempotency-Key`; replaying a registration creates a new patient.
- **E2E tests.** Local Testcontainers has no usable Docker socket in this environment; CI runs the full suite including PostgreSQL LISTEN/NOTIFY.

## Architecture and decisions

Spring Boot 3.5.16 / Java 21, Spring WebFlux, R2DBC + Flyway, Kafka with transactional outbox, Redis projections, pgvector. Services own separate databases (ADR-14). See [`docs/adr/`](docs/adr/), [`docs/services/`](docs/services/) and [`docs/requirements.md`](docs/requirements.md).

**Production note:** Boot 3.5.16 reached EOL on 2026-06-30. Production requires commercial Spring support or a migration to Boot 4.1+ (ADR-10).
