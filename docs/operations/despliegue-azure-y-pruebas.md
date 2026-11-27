# Despliegue en Kubernetes y registro de pruebas

Este documento deja constancia de **cómo se subió la plataforma a AKS**, qué se verificó el **26 de noviembre de 2026 (hora local)** y qué queda pendiente. El resultado inmediato es una URL HTTPS pública con los dos servicios y el Gateway operativos; el despliegue exitoso se logró con el commit `45a189a6` y **workarounds manuales** que ya están resueltos en el commit `f704877`, pero aún no desplegados.

> **Alcance:** el despliegue y las pruebas se realizaron en el repositorio `rgranados1995/vidacruz-triage-platform`. Los IDs de run y las referencias a hallazgos corresponden a ese repositorio; ambos commits citados (`45a189a6` y `f704877`) están presentes también en este repositorio.

| Dato | Valor |
|---|---|
| Host público | `https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com` |
| Suscripción / región | Azure for Students / `northcentralus` |
| Resource group | `vidacruz-rg` |
| Cluster AKS | `vidacruz-aks` (1 nodo `Standard_B4as_v2`) |
| Imagen desplegada | `45a189a6a9d6047fef50b3845c8d4c9ec495c080` |
| `main` actual en el repo de Rafa | `f704877c167dadb505753f545e491cec8c572862` (CI verde en `45936449205` y `45936449238`) |
| Run de deploy | `45927651547` → `failure` por timeout en Kafka |
| Estado de los pods | Kafka `1/1`, Redis `1/1`, triaje `2/2`, asistente `1/1` (bajado a mano) |

---

## Ruta rápida

1. `az login` → `git pull --ff-only origin main` → `az aks install-cli`.
2. Revisar/rotar los secrets del environment `azure` (los tres que se pegaron en el chat están comprometidos).
3. Re-corre `infra/azure/provision.ps1` con `-Location northcentralus -NodeVmSize Standard_B4as_v2`: ahora calcula el subject OIDC exacto, activa Entra ID sin cuentas locales, instala cert-manager con SHA-256 verificado y publica las rutas.
4. Lanzar `deploy.yml` con el tag `f704877`: aplica solo los workloads del namespace `vidacruz`, con los requests bajos y **sin scale manual**.
5. Verificar: pods `2/2` en ambos servicios, Gateway `Programmed`, certificado `Ready`, `401 TRG-005` sin key y `200` en `/v3/api-docs`.
6. Ejecutar la prueba E2E de RF-06 por SSE y cerrar los hallazgos de despliegue, OIDC y sizing en el repo de Rafa.

---

## 1. Paso a paso realizado para subir a Kubernetes

### 1.1 Línea base

- Auditoría read-only sobre `main@45a189a6` en el repo de Rafa: RF-01…RF-05 y RN-01…RN-05 en PASS; RF-06 implementado pero sin verificación E2E.
- CI en verde para ese SHA: `triage-service.yml` run `45916716225` y `clinical-assistant-service.yml` run `45916716209`.
- Hallazgos registrados en el repo de Rafa: deploy/URL, seguridad, ArchUnit, RN-05 y subject OIDC.

### 1.2 Aprovisionamiento de Azure

```powershell
$env:POSTGRES_PASSWORD = '<16+ caracteres>'
./infra/azure/provision.ps1 `
  -ResourceGroup vidacruz-rg `
  -Location northcentralus `
  -GithubRepository rgranados1995/vidacruz-triage-platform `
  -NodeVmSize Standard_B4as_v2
```

- El primer intento con el default `Standard_B4ms` (`infra/azure/main.bicep:15` en `45a189a6`) falló: `The VM size of Standard_B4ms is not allowed in your subscription in location 'northcentralus'`.
- Hubo un primer `az login` rechazado con `AADSTS50076 ... InteractionRequired` (MFA); se completó el challenge y el script terminó con éxito.
- El script imprimió el host público y las variables/secrets que debía tener GitHub (`infra/azure/provision.ps1:44-51`):

```text
AZURE_CLIENT_ID       = 85f77264-20b6-46c0-a08c-e18a5da2df83
AZURE_TENANT_ID       = fd69ce1b-20c6-42ec-b54e-6d1b3870ac6e
AZURE_SUBSCRIPTION_ID = 99d92ffc-2661-426a-8229-c3c5f7b58da5
AZURE_RESOURCE_GROUP  = vidacruz-rg
```

- Recursos creados en `vidacruz-rg`: AKS `vidacruz-aks`, ACR `vidacruzl7rsr6`, PostgreSQL `vidacruz-l7rsr6-pg`, IPs `vidacruz-ingress` y `vidacruz-aks-egress`, identidades `vidacruz-github-deploy`, `vidacruz-aks-identity`, `vidacruz-aks-agentpool`, `webapprouting-vidacruz-aks`.

### 1.3 Configuración de GitHub

- Variables en **Settings → Environments → azure → Variables**: las cuatro de arriba.
- Secrets en el mismo environment: `TRIAGE_API_KEY`, `ASSISTANT_API_KEY`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD` y `ANTHROPIC_API_KEY` (opcional; sin ella el asistente queda degradado, `application.yaml:34` y `ClaudeConfig.java:27-31`).
- `.github/workflows/deploy.yml:21-39` es `workflow_dispatch`, usa OIDC (`id-token: write`) y pasa esos secrets como variables de entorno a `deploy.ps1`.

### 1.4 Subject OIDC

- El primer run de `deploy.yml` (`45927651547`) falló en `azure/login` con `AADSTS700213: No matching federated identity record found for presented assertion subject 'repo:rgranados1995@63021833/vidacruz-triage-platform@1472061967:environment:azure'`.
- Se corrigió **a mano** en Azure con `az identity federated-credential update`, dejando el subject con el formato inmutable con IDs del dueño y del repositorio.
- En `45a189a6` el Bicep todavía generaba el formato viejo (`main.bicep:251`, hallazgo de OIDC). `f704877` ya lo corrige: `githubSubject` calculado por `provision.ps1` desde la API de GitHub.

### 1.5 Ejecución del deploy y bloqueo de Kafka

Tras el fix OIDC se re-lanzó el workflow. Llegó hasta aplicar todos los manifiestos:

```text
deployment "cert-manager" successfully rolled out
deployment "cert-manager-webhook" successfully rolled out
deployment "cert-manager-cainjector" successfully rolled out
clusterissuer.cert-manager.io/letsencrypt created
namespace/vidacruz-edge created
gateway.gateway.networking.k8s.io/vidacruz created
httproute.gateway.networking.k8s.io/https-redirect created
namespace/vidacruz created
configmap/clinical-assistant-service-config created
configmap/triage-service-config created
service/... created
deployment.apps/clinical-assistant-service created
deployment.apps/triage-service created
statefulset.apps/kafka created
statefulset.apps/redis created
poddisruptionbudget.policy/... created
httproute.gateway.networking.k8s.io/vidacruz-api created
httproute.gateway.networking.k8s.io/vidacruz-docs-assistant created
httproute.gateway.networking.k8s.io/vidacruz-docs-triage created
Waiting for 1 pods to be ready...
```

- Los cuatro `Waiting...` eran reintentos del mismo `rollout status`, no un workload por línea. El orders de espera es kafka → redis → triaje → asistente (`infra/azure/deploy.ps1:139-141` en `45a189a6`).
- `kubectl get pods -n vidacruz -o wide` mostró `kafka-0` en `Pending`; el resto estaba `Running`.
- `kubectl describe pod -n vidacruz kafka-0`: `FailedScheduling: 0/1 nodes are available: 1 Insufficient cpu`. Kafka pedía `250m` y `1Gi` (`infra/k8s/kafka.yaml:93-98`).
- `kubectl describe node aks-system-40351138-vmss000000`: `cpu 3727m (96%)`, `memory 8546Mi (72%)`.
- `az aks nodepool scale --name system --node-count 2` falló con `ErrCode_InsufficientVCPUQuota`: quedan 2 vCPU regionales y el nodo extra pide 4. Descartado por cuota de Azure for Students.
- Workaround aplicado en el entorno:

```powershell
kubectl scale deployment clinical-assistant-service -n vidacruz --replicas=1
```

- `kafka-0` pasó a `Running 1/1`. El pod del asistente que apareció en `Error` era el que se estaba terminando por el scale-down; el que quedó tiene `0` restarts.
- El run expiró por el timeout de 600 s (`deploy.ps1:141`) y quedó en `failure`. Lo que faltó se completó a mano:

```powershell
kubectl -n vidacruz-edge wait --for condition=Programmed gateway/vidacruz --timeout=300s
kubectl -n vidacruz-edge wait --for condition=Ready certificate/vidacruz-tls --timeout=600s
```

Ambos devolvieron `condition met`.

### 1.6 Rutas y capa edge

- `gateway/vidacruz`: `Programmed=True`, IP `20.221.226.93`.
- `certificate/vidacruz-tls`: `Ready=True`, secret `vidacruz-tls`, emitido por Let's Encrypt.
- En `vidacruz-edge` solo está `https-redirect`; las tres rutas de API/Swagger viven en `vidacruz` porque `infra/k8s/kustomization.yaml:7` fija ese namespace.
- `kubectl -n vidacruz get httproute` → `vidacruz-api`, `vidacruz-docs-triage`, `vidacruz-docs-assistant`, todas con `Accepted=True` y `ResolvedRefs=True` para el hostname público.
- En `45a189a6`, `/docs/triage/swagger-ui.html` daba 404: la regla `PathPrefix /docs/triage/swagger-ui` no cubre el sufijo `.html` y el redirect de springdoc perdía el prefijo. `f704877` agrega una regla `Exact` para ese path (`infra/k8s/routes/routes.yaml`).

### 1.7 Procedimiento limpio con `f704877`

`f704877` cierra los hallazgos del despliegue:

| Cambio | Antes (`45a189a6`) | Después (`f704877`) |
|---|---|---|
| Subject OIDC | formato viejo generado por Bicep | calculado por `provision.ps1` con los IDs del repo |
| Acceso al cluster | `--admin`, Cluster Admin para el workflow | Entra ID + Azure RBAC, sin cuentas locales; workflow solo escribe en `vidacruz` |
| Preparación del cluster | la hacía `deploy.ps1` | la hace `provision.ps1` (cert-manager con SHA-256, Gateway, namespaces, rutas) |
| Requests de CPU | 1850m totales → nodo al 96 % | 850m totales (Kafka 100m, Redis 50m, triaje 100m ×2, asistente 250m ×2) |
| Swagger | `/swagger-ui.html` → 404 | regla `Exact` que reescribe a `/swagger-ui.html` con `X-Forwarded-Prefix` |
| README | “URL en Azure: se completa al desplegar” | URL real y rutas de Swagger documentadas |

Secuencia recomendada:

```powershell
git pull --ff-only origin main
az aks install-cli          # kubectl + kubelogin
az login
az account set --subscription 99d92ffc-2661-426a-8229-c3c5f7b58da5

$env:POSTGRES_PASSWORD = '<16+ caracteres>'
./infra/azure/provision.ps1 -ResourceGroup vidacruz-rg -Location northcentralus `
  -GithubRepository rgranados1995/vidacruz-triage-platform -NodeVmSize Standard_B4as_v2
```

Aviso importante: al activar Entra ID y `disableLocalAccounts: true`, el kubeconfig `--admin` usado hasta ahora deja de servir. Hay que entrar con `kubelogin` (lo hace `common.ps1` en `provision.ps1`/`deploy.ps1`). Después:

```powershell
gh workflow run deploy.yml --repo rgranados1995/vidacruz-triage-platform
gh run watch --repo rgranados1995/vidacruz-triage-platform
```

No hace falta el `kubectl scale`: con 850m pedidos caben las dos réplicas del asistente en el mismo nodo.

---

## 2. Pruebas realizadas el 26 de noviembre (hora local)

### 2.1 Código y CI

| Prueba | Resultado |
|---|---|
| Auditoría RF-01…RF-05, RN-01…RN-05 | PASS en `45a189a6`; RF-06 implementado sin E2E |
| `triage-service.yml` @ `45a189a6` | `success`, run `45916716225` |
| `clinical-assistant-service.yml` @ `45a189a6` | `success`, run `45916716209` |
| Runs anteriores | `45913361321` y `45914065087`, ambos `success` |
| CI @ `f704877` (fix de hallazgos) | `success`, runs `45936449205` y `45936449238` |
| Comprobación de proveedor LLM | Una key de Gemini en `ANTHROPIC_API_KEY` no funciona: el SDK es `com.anthropic:anthropic-java:3.8.0` (`claude-llm/build.gradle:8`), `base-url` fijo a `api.anthropic.com` (`application.yaml:35`) y un 401 se mapea a `ASSISTANT_UNAVAILABLE` (`ClaudeAnswerGenerator.java:60-61`) |

### 2.2 Infraestructura y despliegue

| Comando / acción | Resultado |
|---|---|
| `az aks nodepool scale --node-count 2` | Falló: `ErrCode_InsufficientVCPUQuota` (quedan 2 vCPU, pide 4) |
| `kubectl describe pod -n vidacruz kafka-0` | `Pending`, `0/1 nodes ... 1 Insufficient cpu` |
| `kubectl describe node aks-system-40351138-vmss000000` | `cpu 3727m (96%)`, `memory 8546Mi (72%)` |
| `kubectl scale deployment clinical-assistant-service --replicas=1` | `deployment.apps/clinical-assistant-service scaled`; Kafka pasó a `Running 1/1` |
| `kubectl get pods -n vidacruz -o wide` | Kafka `1/1`, Redis `1/1`, triaje `2/2`, asistente `1/1` con 0 restarts |
| Gateway y certificado | `Programmed=True` (`20.221.226.93`) y `Ready=True` |
| `kubectl -n vidacruz get httproute` | 3 rutas `Accepted=True`, `ResolvedRefs=True`, hostname correcto |
| Hallazgo de sizing/CPU | Registrado con el análisis de CPU y comentado con el fallo de cuota |

### 2.3 HTTP sobre la URL pública

| Request | Resultado | Interpretación |
|---|---|---|
| `GET /triage/pacientes` sin key | `401`, `codigo: TRG-005`, `x-envoy-upstream-service-time: 4` | Llegó al servicio y se aplica `X-API-Key` |
| `GET /docs/triage/v3/api-docs` | `200`, OpenAPI 3.1.0, 15720 bytes | Contrato publicado correctamente |
| `GET /docs/triage/swagger-ui/index.html` | `200`, HTML de Swagger UI | UI accesible |
| `GET /docs/triage/swagger-ui.html -L` | `404` de Envoy, sin header de upstream | `PathPrefix` no cubre `.html`; corregido en `f704877` |
| `GET /health/readiness` (público) | `404` de `istio-envoy` | `/health` no se publica (`infra/k8s/routes.yaml:1-3` en `45a189a6`) |
| `GET http://triage-service.vidacruz.svc.cluster.local/health/readiness` desde Postman | `getaddrinfo ENOTFOUND` | DNS de cluster solo resuelve dentro de AKS, además faltaba el puerto `8081` |

La forma correcta de consultar la salud desde la PC es:

```powershell
kubectl port-forward -n vidacruz svc/triage-service 8081:8081
curl.exe -i http://127.0.0.1:8081/health/readiness
```

No requiere API key: `/health/**` está en `PUBLIC_PATHS` con `permitAll` (`ApiKeySecurityConfig.java:35,55`).

### 2.4 Observabilidad y costos

- Se verificó que **no hay Grafana ni Jaeger en Azure**: solo están en `docker-compose.yml:62-96` y se documentan en `README.md:58-60`. En AKS no hay manifiestos de esa stack; las trazas no se exportan (`triage-service/deployment/k8s/configmap.yaml:10-11`) y el ADR lo deja como evolución (`docs/adr/0004-despliegue-en-azure.md:65-68` en `45a189a6`).
- Métricas crudas disponibles dentro del cluster en `/prometheus` de cada servicio (`application.yaml:104`).
- Revisión de costos: portal **Cost Management + Billing → Cost analysis**, filtro `Resource group = vidacruz-rg`; estimación del ADR: 140–150 USD/mes, con AKS y PostgreSQL detenibles cuando no se usan (`docs/adr/0004-despliegue-en-azure.md:62`).

---

## 3. Pendiente por realizar

### Urgente

- [ ] **Rotar `TRIAGE_API_KEY`, `ASSISTANT_API_KEY` y `REDIS_PASSWORD`.** Los valores se pegaron en el chat; deben considerarse expuestos. Actualizar también el environment `azure` y la colección de Postman (`docs/postman/VidaCruz.postman_collection.json`).

### Para cerrar el despliegue reproducible

- [ ] `git pull --ff-only origin main` para traer `f704877`.
- [ ] Re-corre `provision.ps1` con Entra ID/kubelogin: fija subject OIDC, RBAC, cert-manager verificado y rutas. Confirmar que el workflow ya no tiene Cluster Admin.
- [ ] Re-lanzar `deploy.yml` con el tag `f704877` y verificar que **no hace falta** el `kubectl scale`: asistente `2/2` y triaje `2/2`.
- [ ] Confirmar que `/docs/triage/swagger-ui.html` y `/docs/asistente/swagger-ui.html` responden con la nueva regla `Exact`.
- [ ] Revisar `swagger-initializer.js` para que `configUrl` incluya `/docs/triage` (el HTML ya daba 200, pero faltaba confirmar que la UI carga el spec).
- [ ] Cerrar los hallazgos de deploy, OIDC y sizing en el repo de Rafa; revisar el de superficie pública y privilegios.

### Verificación funcional

- [ ] `POST /asistente/consulta` con API key. Sin `ANTHROPIC_API_KEY`, esperado `503 AST-002` en modo degradado; con key válida de Anthropic, esperado `200`.
- [ ] **RF-06 E2E por SSE**: registrar un paciente crítico, dejar vencer el SLA de RN-02, abrir `GET /triage/alertas/stream`, Confirmar el evento `alerta` con su `actualizadaEn`, reconocerlo con `POST /triage/alertas/{id}/reconocimiento` y verificar la transición a `RECONOCIDA`.
- [ ] Verificar que el payload de la alerta **no incluya** nombre ni queja principal del paciente (regla registrada en el handoff de RF-06).
- [ ] Swagger del asistente y del triaje con el botón **Authorize** usando la API key rotada.

### Operación y costos

- [ ] Crear alertas de presupuesto en **Cost Management → Budgets** (80 % y 100 %).
- [ ] Mientras dure la evaluación: `az aks stop` + `az postgres flexible-server stop` cuando no se use; `start` en orden inverso (PostgreSQL primero).
- [ ] Al entregar: `az group delete -g vidacruz-rg --yes --no-wait` para llevar el costo a cero, después de guardar la evidencia de las pruebas.
- [ ] Opcional y no recomendado: publicar `/health` agregando una ruta al repo. Hoy es interno por decisión de diseño.
- [ ] Opcional: Grafana/Jaeger en Azure. No está implementado; el ADR lo describe como evolución con Azure Monitor y Azure Managed Grafana.

---

## Next step

El siguiente paso concreto es la rotación de secrets y el re-despliegue con `f704877`. Después de eso, la única prueba funcional que aún falta de verdad es **RF-06 por SSE**; es la que cierra el entregable y permite dar por terminado el hallazgo de despliegue en el repo de Rafa.
