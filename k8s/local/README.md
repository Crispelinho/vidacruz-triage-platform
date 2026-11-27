# Local Kubernetes stack

Self-contained Kubernetes bundle for running both services on Minikube or Docker Desktop Kubernetes. It is intentionally separate from `docker-compose.yml`, CI and every Azure script: nothing else in this repository references `k8s/local`, and the manifests are applied only by the explicit commands below while `kubectl` points at the local context. No Azure subscription, public URL, federated credential or real API key is involved.

The demo passwords and API keys in the ConfigMaps are the public defaults already present in `docker-compose.yml` and the root `README.md`. Never reuse them outside this namespace.

## Prerequisites

- Docker with the Compose v2 install (used only to build the two service images)
- `minikube` or Docker Desktop Kubernetes, plus `kubectl` on the PATH
- Suggested size: 4 CPUs / 6 GiB memory so the broker and both JVMs fit

Docker Desktop users: enable Kubernetes and select the `docker-desktop` context. This bundle was verified there with `kubeadm v1.34.1`; add `--context docker-desktop` to every `kubectl` command below. Do not use the AKS context while applying this overlay.

## Start

Run from the repository root:

```bash
minikube start --driver=docker --cpus=4 --memory=6144
kubectl config use-context minikube

# Docker Desktop instead:
# kubectl config use-context docker-desktop

docker build -t vidacruz/triage-service:local ./triage-service/triage-service
docker build -t vidacruz/clinical-assistant-service:local ./clinical-assistant-service/clinical-assistant-service

minikube image load vidacruz/triage-service:local
minikube image load vidacruz/clinical-assistant-service:local

kubectl kustomize ./k8s/local          # optional: render everything without contacting a cluster
kubectl apply -k ./k8s/local
kubectl -n vidacruz-local rollout status deploy/triage-db
kubectl -n vidacruz-local rollout status deploy/assistant-db
kubectl -n vidacruz-local rollout status deploy/kafka
kubectl -n vidacruz-local rollout status deploy/redis
kubectl -n vidacruz-local rollout status deploy/jaeger
kubectl -n vidacruz-local rollout status deploy/triage-service
kubectl -n vidacruz-local rollout status deploy/clinical-assistant-service
```

The workloads are reachable only inside the cluster. Open one terminal per service:

```bash
kubectl -n vidacruz-local port-forward svc/triage-service 18086:80
kubectl -n vidacruz-local port-forward svc/clinical-assistant-service 18085:80
kubectl -n vidacruz-local port-forward svc/jaeger 18087:16686
```

Use `127.0.0.1`, not `localhost`: on Windows `localhost` may resolve to `::1`, where a stale AKS port-forward can answer with a different stack. Pick a fresh high port if a forward dies when a pod restarts, and verify `/actuator/health` before sending business requests. The broker is `apache/kafka:3.9.1` on `kafka:9092` (KRaft, no `CLUSTER_ID`); Flyway needs `SPRING_DATASOURCE_*` in addition to `R2DBC_URL`, and the assistant needs `SPRING_R2DBC_*` plus the PgVector auto-configuration exclusion because its vector bean is defined locally.

## Verify

```bash
curl -fsS http://127.0.0.1:18086/actuator/health            # {"status":"UP"}
curl -fsS http://127.0.0.1:18085/actuator/health            # {"status":"UP"}
curl -sS -i http://127.0.0.1:18086/triage/alertas           # 401 UNAUTHORIZED
curl -sS -H 'X-API-Key: test-triage-key' \
  http://127.0.0.1:18086/triage/alertas                     # 200 []
# On PowerShell, write the JSON to a UTF-8 (no BOM) file first; inline -d loses the quotes.
curl -sS -H 'X-API-Key: test-triage-key' \
  -X POST http://127.0.0.1:18086/triage/pacientes \
  -H 'Content-Type: application/json' -d @patient.json
curl -N -H 'X-API-Key: test-triage-key' \
  http://127.0.0.1:18086/triage/alertas/stream              # snapshot + latido heartbeats
curl -sS http://127.0.0.1:18087/                            # Jaeger UI
```

The report endpoint rejects ranges over 90 days; use at most 60 days in the demo. Nested Gradle wrappers need LF endings (`.gitattributes` normalizes `gradlew`) or `gradlew build` fails inside the Linux image.

The complete E1-E6 / SLA / report / auth / metrics checklist is in [`docs/demo/local-verification.md`](../../docs/demo/local-verification.md); the same requests are in [`postman/`](../../postman/). Use the port-forward URLs above in place of the Compose ports.

Expected degraded path: `/rag` returns `503 LLM_UNAVAILABLE` because Ollama is not deployed here. That is the documented fallback, not a broken stack. To exercise a real provider, edit `assistant-local-config` in the local cluster and set `RAG_LLM_CHAIN` plus the provider key — never commit the value.

## Stop and clean up

```bash
kubectl delete -k ./k8s/local
minikube stop          # or: kubectl config use-context docker-desktop
```

Database data lives in `emptyDir` volumes, so deleting the namespace discards it. `minikube delete` removes the cluster and the locally loaded images.

## Relationship to production

This bundle does not reuse, patch or reference any Azure manifest, does not create a Gateway or cert-manager resources, and does not publish any route. Applying it cannot change an AKS deployment; only an explicit deploy against that cluster's kubeconfig does that.
