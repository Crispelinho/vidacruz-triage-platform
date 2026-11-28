param(
    [string]$Kubeconfig = 'C:\Users\HP\AppData\Local\Temp\vidacruz-vidacruz-aks.kubeconfig',
    [string]$ReportPath = (Join-Path $env:TEMP 'azure-db-rowlevel.txt'),
    [string]$PatientId = '16476d7d-1d50-4157-b406-9e9edc2958e5',
    [string]$TraceId = '7c2e9b41d8a64f30b5e7c1a9f2d6b845'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PATH = 'C:\Users\HP\.azure-kubectl;C:\Users\HP\.azure-kubelogin;C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin;' + $env:PATH
$kc = $Kubeconfig
$report = $ReportPath
$manifest = Join-Path $env:TEMP 'vidacruz-psql-verify.yaml'
$sqlPath = Join-Path $env:TEMP 'vidacruz-psql-verify.sql'
$pod = 'vidacruz-psql-verify'

$patientId = $PatientId
$traceId = $TraceId

$yaml = @"
apiVersion: v1
kind: Pod
metadata:
  name: $pod
  namespace: vidacruz
  labels:
    app: vidacruz-psql-verify
spec:
  restartPolicy: Never
  securityContext:
    runAsNonRoot: true
    runAsUser: 70
    runAsGroup: 70
    fsGroup: 70
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: psql
      image: postgres:16-alpine
      command: ['/bin/sh', '-c', 'sleep 1800']
      securityContext:
        allowPrivilegeEscalation: false
        runAsNonRoot: true
        capabilities:
          drop: ['ALL']
      env:
        - name: PGHOST
          value: vidacruz-l7rsr6-pg.postgres.database.azure.com
        - name: PGPORT
          value: '5432'
        - name: PGDATABASE
          value: triage
        - name: PGSSLMODE
          value: require
        - name: PGUSER
          valueFrom:
            configMapKeyRef:
              name: triage-service-config
              key: SPRING_DATASOURCE_USERNAME
        - name: PGPASSWORD
          valueFrom:
            secretKeyRef:
              name: triage-service-secrets
              key: SPRING_DATASOURCE_PASSWORD
"@
[IO.File]::WriteAllText($manifest, $yaml, (New-Object Text.UTF8Encoding($false)))

$sql = @"
\set ON_ERROR_STOP on
\echo === CONNECTIVITY ===
SELECT current_database() AS db, inet_server_addr() AS server_ip, inet_server_port() AS port;
\echo === PATIENT TOTALS ===
SELECT count(*) AS patients FROM paciente;
SELECT nivel, estado, count(*) AS n FROM paciente GROUP BY nivel, estado ORDER BY nivel, estado;
\echo === AZURE VERIFICATION PATIENT ===
SELECT id, nivel, sla_minutos, estado, escalado, fecha_registro
FROM paciente WHERE id = '$patientId';
\echo === OUTBOX ROWS FOR THAT PATIENT ===
SELECT event_type, schema_version, (publicado_en IS NOT NULL) AS published,
       intentos, requiere_revision, traceparent
FROM outbox_evento WHERE paciente_id = '$patientId' ORDER BY occurred_at;
\echo === TRACE ID MATCHES IN OUTBOX ===
SELECT count(*) AS rows_with_trace FROM outbox_evento WHERE traceparent LIKE '%$traceId%';
SELECT event_type, paciente_id, (publicado_en IS NOT NULL) AS published, traceparent
FROM outbox_evento WHERE traceparent LIKE '%$traceId%' ORDER BY occurred_at;
\echo === AUDIT TRANSITIONS ===
SELECT estado_anterior, estado_nuevo, tipo_evento, creado_en
FROM auditoria_clinica WHERE paciente_id = '$patientId' ORDER BY creado_en;
\echo === MEDICAL ALERT ===
SELECT id, nivel, estado, minutos_espera,
       (reconocida_en IS NOT NULL) AS acknowledged,
       (resuelta_en IS NOT NULL) AS resolved
FROM medical_alert WHERE patient_id = '$patientId';
\echo === FAILED OUTBOX ROWS ===
SELECT count(*) AS needing_review FROM outbox_evento WHERE requiere_revision;
"@
[IO.File]::WriteAllText($sqlPath, $sql, (New-Object Text.UTF8Encoding($false)))

$lines = New-Object System.Collections.ArrayList
[void]$lines.Add("=== Azure PostgreSQL row-level verification $(Get-Date -Format o) ===")
[void]$lines.Add('Pod runs inside AKS and uses the aks-egress IP allowed by the PostgreSQL firewall rule.')
[void]$lines.Add('Credentials are injected from Kubernetes Secret via secretKeyRef and are never printed.')
[void]$lines.Add('Only SELECT statements are issued. No patient names or complaints are read.')

$created = $false
try {
  & kubectl --kubeconfig $kc --request-timeout=90s -n vidacruz delete pod $pod --ignore-not-found=true 2>&1 | Out-Null
  & kubectl --kubeconfig $kc --request-timeout=90s apply -f $manifest 2>&1 | ForEach-Object { [void]$lines.Add("apply: $_") }
  $created = $true
  & kubectl --kubeconfig $kc --request-timeout=180s -n vidacruz wait --for=condition=Ready pod/$pod --timeout=180s 2>&1 | ForEach-Object { [void]$lines.Add("wait: $_") }
  Start-Sleep -Seconds 3
  Get-Content $sqlPath | & kubectl --kubeconfig $kc --request-timeout=180s -n vidacruz exec -i $pod -- psql -X -A -t -v ON_ERROR_STOP=1 2>&1 | ForEach-Object { [void]$lines.Add($_) }
} catch {
  [void]$lines.Add("ERROR: $($_.Exception.Message)")
  $failed = $true
} finally {
  if ($created) {
    & kubectl --kubeconfig $kc --request-timeout=90s -n vidacruz delete pod $pod --ignore-not-found=true 2>&1 | ForEach-Object { [void]$lines.Add("cleanup: $_") }
  }
}

$lines | Out-File -FilePath $report -Encoding utf8
$lines | ForEach-Object { Write-Output $_ }
Write-Output "Report: $report"
if ($failed) { exit 1 }
