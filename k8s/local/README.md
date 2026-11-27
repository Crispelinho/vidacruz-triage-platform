# Minikube local stack

Self-contained Kubernetes bundle for running both services on Minikube. It is intentionally separate from `docker-compose.yml`, CI and every Azure script: nothing else in this repository references `k8s/local`, and the manifests are applied only by the explicit commands below while `kubectl` points at the Minikube context. No Azure subscription, public URL, federated credential or real API key is involved.

The demo passwords and API keys in the ConfigMaps are the public defaults already present in `docker-compose.yml` and the root `README.md`. Never reuse them outside this namespace.

## Prerequisites

- Docker with the Compose v2 install (used only to build the two service images)
- `minikube` and `kubectl` on the PATH
- Suggested size: 4 CPUs / 6 GiB memory so the broker and both JVMs fit

## Start

Run from the repository root:

```bash
minikube start --driver=docker --cpus=4 --memory=6144
kubectl config use-context minikube

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
kubectl -n vidacruz-local port-forward svc/triage-service 8081:80
kubectl -n vidacruz-local port-forward svc/clinical-assistant-service 8082:80
kubectl -n vidacruz-local port-forward svc/jaeger 16686:16686
```

## Verify

```bash
curl -fsS http://localhost:8081/actuator/health            # {"status":"UP"}
curl -fsS http://localhost:8082/actuator/health            # {"status":"UP"}
curl -sS -i http://localhost:8081/triage/alertas           # 401 UNAUTHORIZED
curl -sS -H 'X-API-Key: test-triage-key' \
  http://localhost:8081/triage/alertas                     # 200 []
curl -sS -H 'X-API-Key: test-triage-key' \
  -X POST http://localhost:8081/triage/pacientes \
  -H 'Content-Type: application/json' \
  -d '{"nombre_completo":"Paciente E1","edad":65,"frecuencia_cardiaca":130,"presion_sistolica":180,"presion_diastolica":110,"spo2":97,"temperatura":36.8,"dolor":8,"gcs":15,"queja_principal":"Dolor torácico"}'
curl -N -H 'X-API-Key: test-triage-key' \
  http://localhost:8081/triage/alertas/stream              # snapshot + latido heartbeats
curl -sS http://localhost:16686/                           # Jaeger UI
```

The complete E1-E6 / SLA / report / auth / metrics checklist is in [`docs/demo/local-verification.md`](../../docs/demo/local-verification.md); the same requests are in [`postman/`](../../postman/). Use the port-forward URLs above in place of the Compose ports.

Expected degraded path: `/rag` returns `503 LLM_UNAVAILABLE` because Ollama is not deployed here. That is the documented fallback, not a broken stack. To exercise a real provider, edit `assistant-local-config` in the local cluster and set `RAG_LLM_CHAIN` plus the provider key — never commit the value.

## Stop and clean up

```bash
kubectl delete -k ./k8s/local
minikube stop
```

Database data lives in `emptyDir` volumes, so deleting the namespace discards it. `minikube delete` removes the cluster and the locally loaded images.

## Relationship to production

This bundle does not reuse, patch or reference any Azure manifest, does not create a Gateway or cert-manager resources, and does not publish any route. Applying it cannot change an AKS deployment; only an explicit deploy against that cluster's kubeconfig does that.
