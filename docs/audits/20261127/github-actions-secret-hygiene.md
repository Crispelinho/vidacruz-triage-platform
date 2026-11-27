# GitHub Actions secret hygiene — 2026-11-27

**Alcance:** evidencia parcial para la compuerta de higiene de secretos de ADR-11. Este documento **no** cambia el estado de ningún ADR y **no** registra valores de credenciales.

## Comandos ejecutados y resultado observado

| Comando | Resultado |
|---|---|
| `gh secret list --repo Crispelinho/vidacruz-triage-platform` | Seis nombres de secret de repositorio, con fecha de actualización: `ASSISTANT_API_KEY` (2026-11-27T16:19:33Z), `GEMINI_API_KEY` (2026-11-27T17:07:20Z), `OPENCODE_API_KEY` (2026-11-27T17:06:38Z), `POSTGRES_PASSWORD` (2026-11-27T21:10:54Z), `REDIS_PASSWORD` (2026-11-27T16:21:43Z), `TRIAGE_API_KEY` (2026-11-27T16:23:15Z). La API no devuelve valores. |
| `gh secret list --repo Crispelinho/vidacruz-triage-platform --env azure` | `HTTP 404: Not Found`. |
| `gh api repos/Crispelinho/vidacruz-triage-platform/environments` | `{"total_count":0,"environments":[]}`. No hay environments configurados. |
| `git grep -n -I -E 'AIza[0-9A-Za-z_-]{20,}|sk-or-v1-[0-9A-Za-z_-]{10,}|ghp_[0-9A-Za-z]{20,}|github_pat_[0-9A-Za-z_]{20,}|x-goog-api-key\s*:\s*\S+' -- . ':!**/build/**' ':!**/.gradle/**'` | Sin coincidencias en archivos versionados. |
| `git log --all --oneline -S'AIza' -S'sk-or-v1-' -S'ghp_' -- .` | Sin coincidencias en el historial consultado. |

## Revisión de workflows

Los seis workflows presentes son `ci.yml`, `clinical-assistant-service.yml`, `deploy.yml`, `gemini-embedding-verification.yml`, `k8s-manifests.yml` y `triage-service.yml`.

- `.github/workflows/deploy.yml`: `workflow_dispatch`, `permissions: contents: read` + `id-token: write`, `environment: azure`, y `azure/login` con `vars.AZURE_CLIENT_ID`, `vars.AZURE_TENANT_ID` y `vars.AZURE_SUBSCRIPTION_ID`. No guarda contraseña de Azure en GitHub. Los seis secretos se pasan como variables de entorno a `infra/azure/deploy.ps1` (`deploy.yml:39-46`).
- `.github/workflows/gemini-embedding-verification.yml`: `workflow_dispatch`, inyecta `secrets.GEMINI_API_KEY` como variable de entorno y comprueba su presencia con `test -n "$GEMINI_API_KEY"` (`gemini-embedding-verification.yml:23-34`).
- Los otros cuatro workflows no referencian `secrets.*`.
- Ningún workflow usa `pull_request_target`.

Los valores en `.env.example` y `docker-compose.yml` son placeholders locales (`test-*`, `*-local` y keys vacías), no credenciales de despliegue.

## Hallazgos

1. No se encontraron valores con formato de credencial en archivos versionados ni en el historial para los patrones consultados.
2. El acceso a Azure usa OIDC; no hay contraseña de Azure almacenada como secreto.
3. Los dos workflows que consumen secretos son de ejecución manual (`workflow_dispatch`).
4. **Brecha:** `deploy.yml` declara `environment: azure`, pero el repositorio no tiene environments configurados (`total_count: 0`). No hay secretos ni reglas de protección (revisores, temporizador) scoped al entorno; los secretos disponibles son de repositorio.
5. **Brecha:** no se encontró registro de rotación de las credenciales que `docs/operations/despliegue-azure-y-pruebas.md:27,260` marca como expuestas en chat y pendientes de rotar.
6. **Brecha:** no se ejecutó escaneo de capas de imagen ni de historial con patrones distintos a los consultados aquí.

## Límite de validez

Esta evidencia cubre solo repositorio y GitHub Actions. No cierra la compuerta de higiene de secretos de ADR-11: siguen abiertos el registro de rotación, el escaneo de imagen y cualquier evidencia a nivel de cuenta que no pueda comprobarse desde el repositorio. No se registran valores de secretos, prompts, respuestas, identificadores de cuenta ni identificadores de tenant.
