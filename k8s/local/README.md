# Local Kubernetes overlay

Local-only verification on Docker Desktop Kubernetes or Minikube. It reuses `infra/k8s` workloads and adds
local images, placeholder Secrets, one local PostgreSQL instance, and cluster-default storage. It contains no
Ingress, Gateway, TLS material, public route, cloud resource, or real credential. The commands below are a
verification procedure, not a claim that the cluster run has already happened.

## Build and apply

Run from the repository root. Ensure both Gradle wrappers have LF endings before building on Windows.

```bash
docker build -t triage-service:local ./triage-service/triage-service
docker build -t clinical-assistant-service:local ./clinical-assistant-service/clinical-assistant-service
```

On Minikube, load the images into the cluster daemon:

```bash
minikube image load triage-service:local
minikube image load clinical-assistant-service:local
```

Render first, then apply:

```bash
kubectl kustomize ./k8s/local
kubectl apply -k ./k8s/local
kubectl -n vidacruz rollout status statefulset/local-db
kubectl -n vidacruz rollout status statefulset/kafka
kubectl -n vidacruz rollout status statefulset/redis
kubectl -n vidacruz wait --for=condition=complete job/kafka-topic-init --timeout=5m
kubectl -n vidacruz rollout status deployment/triage-service
kubectl -n vidacruz rollout status deployment/clinical-assistant-service
kubectl -n vidacruz rollout status deployment/jaeger
```

## Port-forward

Use three terminals, or run the additional commands with `&`:

```bash
kubectl -n vidacruz port-forward svc/triage-service 8081:8081
kubectl -n vidacruz port-forward svc/clinical-assistant-service 8082:8082
kubectl -n vidacruz port-forward svc/jaeger 16686:16686
```

The local API keys match the placeholder Secrets in this overlay: `local-triage-key` and
`local-assistant-key`. Use synthetic values only.

## Escalation to alert to SSE

Open the SSE stream in one terminal:

```bash
curl -N -H 'X-API-Key: local-triage-key' \
  http://localhost:8081/triage/alertas/stream
```

Register one synthetic patient with critical vitals in another terminal. The overlay shortens `CRITICO` to
one minute so the SLA passes quickly:

```bash
PATIENT_ID=$(curl -sS -X POST http://localhost:8081/triage/pacientes \
  -H 'X-API-Key: local-triage-key' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: local-k8s-check' \
  -d '{"nombre_completo":"local-check","edad":65,"frecuencia_cardiaca":130,"presion_sistolica":180,"presion_diastolica":110,"spo2":97,"temperatura":36.8,"dolor":8,"gcs":15,"queja_principal":"local verification"}' \
  | jq -r '.id')
```

Expect an `alerta` event on the SSE stream within roughly one to two minutes. Confirm the alert row:

```bash
curl -sS -H 'X-API-Key: local-triage-key' \
  http://localhost:8081/triage/alertas \
  | jq '.[] | {id, pacienteId, nivel, estado}'
```

The alert response intentionally carries no patient name or complaint.

## Outbox to Kafka to urgency state

Check that the escalation outbox row was marked published:

```bash
kubectl -n vidacruz exec statefulset/local-db-0 -- \
  psql -U triage_admin -d triage -c \
  "select event_type, publicado_en from outbox_evento order by created_at desc limit 5;"
```

Read the topic from inside the broker pod. The header output must include `traceparent`:

```bash
kubectl -n vidacruz exec statefulset/kafka-0 -- \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic triaje.eventos \
  --property print.headers=true \
  --from-beginning --max-messages 2
```

Then check the projected urgency state:

```bash
curl -sS -H 'X-API-Key: local-assistant-key' \
  http://localhost:8082/asistente/estado-urgencias
```

Expect `totalActive` to include the synthetic patient and the `CRITICO` counter to be at least 1. A zero
projection with events present on the topic means the consumer did not commit; inspect the assistant pod logs.

## Distributed trace

Send the registration with an explicit W3C header, then query Jaeger for the same id. The result must contain
spans from both services; `traceparent` travels only in the Kafka header, never in the event payload.

```bash
TRACE_ID=4bf92f3577b34da6a3ce929d0e0e4736
curl -sS -X POST http://localhost:8081/triage/pacientes \
  -H 'X-API-Key: local-triage-key' \
  -H 'Content-Type: application/json' \
  -H "traceparent: 00-$TRACE_ID-00f067aa0ba902b7-01" \
  -d '{"nombre_completo":"trace-check","edad":65,"frecuencia_cardiaca":130,"presion_sistolica":180,"presion_diastolica":110,"spo2":97,"temperatura":36.8,"dolor":8,"gcs":15,"queja_principal":"trace check"}'

curl -sS "http://localhost:16686/api/traces/$TRACE_ID" | jq -r '.data[].spans[].process.serviceName' | sort -u
```

## Local gotchas

- **LF wrappers:** the Dockerfiles run `./gradlew`. If a Windows checkout converted it to CRLF, the build
  fails with a bad interpreter error. Use LF endings (`git config --global core.autocrlf input`, then
  re-checkout the wrapper files) before building.
- **Kafka topic:** auto-create is disabled. `kafka-topic-init` creates `triaje.eventos`, and both services wait
  for it in an init container. Wait for the Job to complete before reading results.
- **Flyway:** both services migrate their own database at startup. Do not create tables by hand; wait for
  readiness instead. The triage service needs both `R2DBC_URL` and `SPRING_DATASOURCE_URL` for this to happen.
- **Database storage:** `local-db` uses an `emptyDir` with `PGDATA` in a subdirectory because the postgres
  entrypoint cannot chown a hostpath volume under `restricted` PodSecurity. All local data is disposable.
- **Jaeger:** Jaeger 2.x has no `all-in-one` subcommand. The default binary serves the UI on `16686` and OTLP
  on `4317`/`4318`; both services must set `MANAGEMENT_OTLP_TRACING_ENDPOINT`.
- **Report range:** `REPORT_MAX_RANGE` stays `P31D`. Requests covering more than 31 days are rejected.
- **No public URL:** reach both APIs only through `kubectl port-forward`. There is no Ingress, Gateway, TLS
  certificate, or externally routable service in this overlay.
- **RAG:** provider keys are empty placeholders, so questions that need a model return the degraded-mode
  response. That is expected and unrelated to the outbox projection.
