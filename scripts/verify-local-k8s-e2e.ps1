param(
    [string]$TriageBaseUrl = 'http://127.0.0.1:18086',
    [string]$AssistantBaseUrl = 'http://127.0.0.1:18085',
    [string]$JaegerBaseUrl = 'http://127.0.0.1:18087',
    [string]$KubectlContext = 'docker-desktop',
    [string]$Namespace = 'vidacruz',
    [string]$TriageApiKey = 'local-triage-key',
    [string]$AssistantApiKey = 'local-assistant-key',
    [string]$ReportPath = (Join-Path $env:TEMP 'vidacruz-local-k8s-e2e.txt')
)

$ErrorActionPreference = 'Stop'

$triage = $TriageBaseUrl
$assistant = $AssistantBaseUrl
$jaeger = $JaegerBaseUrl
$triageKey = $TriageApiKey
$assistantKey = $AssistantApiKey
$traceId = 'c4e7a1b83d6f20954c7b9e2a6d8f3051'
$spanId = '0b1c2d3e4f506172'
$ssePath = Join-Path $env:TEMP 'vidacruz-local-k8s-sse-v5.txt'
$sseCmd = Join-Path $env:TEMP 'vidacruz-local-k8s-sse-v5.cmd'

$results = New-Object System.Collections.ArrayList

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [string]$TraceParent = $null
    )

    $headersCopy = @{}
    foreach ($key in $Headers.Keys) { $headersCopy[$key] = $Headers[$key] }
    if ($TraceParent) { $headersCopy['traceparent'] = $TraceParent }

    $params = @{
        Method = $Method
        Uri = $Url
        Headers = $headersCopy
        TimeoutSec = 150
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 12 -Compress)
        $params['ContentType'] = 'application/json'
    }

    try {
        $response = $null; for ($attempt = 0; $attempt -lt 4; $attempt++) { try { $response = Invoke-WebRequest @params; break } catch { if ($null -ne $_.Exception.Response -or $attempt -eq 3) { throw }; Start-Sleep -Milliseconds 800 } }
        $status = [int]$response.StatusCode
        # Invoke-WebRequest returns a byte array in this environment; decode it explicitly.
        if ($response.Content -is [byte[]]) {
            $text = [System.Text.Encoding]::UTF8.GetString($response.Content)
        } else {
            $text = [string]$response.Content
        }
        $responseHeaders = $response.Headers
    } catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
        $status = [int]$response.StatusCode
        # Error bodies are only available through ErrorDetails on this PowerShell version.
        $text = $_.ErrorDetails.Message
        if (-not $text) {
            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        $responseHeaders = $response.Headers
    }

    $json = $null
    try { $json = $text | ConvertFrom-Json } catch { }

    return [pscustomobject]@{
        Status = $status
        Text = $text
        Json = $json
        Headers = $responseHeaders
    }
}

function Check {
    param([string]$Name, [bool]$Passed, [string]$Detail)
    $label = if ($Passed) { 'PASS' } else { 'FAIL' }
    [void]$results.Add([pscustomobject]@{ Name = $Name; Passed = $Passed; Detail = $Detail })
    Write-Output ("{0} {1} :: {2}" -f $label, $Name, $Detail)
}

function PatientBody {
    param([string]$Name, [int]$Hr, [int]$Sys, [int]$Dia, [int]$Spo2, [double]$Temp, [int]$Pain, [int]$Gcs, [string]$Complaint)
    return @{
        nombre_completo = $Name
        edad = 55
        frecuencia_cardiaca = $Hr
        presion_sistolica = $Sys
        presion_diastolica = $Dia
        spo2 = $Spo2
        temperatura = $Temp
        dolor = $Pain
        gcs = $Gcs
        queja_principal = $Complaint
    }
}

$triageHeaders = @{ 'X-API-Key' = $triageKey }
$assistantHeaders = @{ 'X-API-Key' = $assistantKey }
$suffix = Get-Random -Minimum 1000 -Maximum 9999

Write-Output "=== Local Kubernetes verification $(Get-Date -Format o) ==="

# Health and UI
$healthT = Invoke-Api -Method GET -Url "$triage/actuator/health"
$healthA = Invoke-Api -Method GET -Url "$assistant/actuator/health"
Check 'Health triage' ($healthT.Status -eq 200 -and $healthT.Json.status -eq 'UP') "status=$($healthT.Status) body=$($healthT.Text)"
Check 'Health assistant' ($healthA.Status -eq 200 -and $healthA.Json.status -eq 'UP') "status=$($healthA.Status) body=$($healthA.Text)"
try {
    $jaegerUi = Invoke-WebRequest -UseBasicParsing -Uri "$jaeger/" -TimeoutSec 10
    Check 'Jaeger UI' ($jaegerUi.StatusCode -eq 200) "status=$([int]$jaegerUi.StatusCode)"
} catch {
    Check 'Jaeger UI' $false "error=$($_.Exception.Message)"
}

# RF-01 E1-E6
$cases = @(
    @{ Id = 'E1'; Hr = 130; Sys = 180; Dia = 110; Spo2 = 97; Temp = 36.8; Pain = 8; Gcs = 15; Complaint = 'Chest pain'; Expected = 'CRITICO' },
    @{ Id = 'E2'; Hr = 95; Sys = 150; Dia = 95; Spo2 = 91; Temp = 37.0; Pain = 4; Gcs = 15; Complaint = 'Breathing difficulty'; Expected = 'URGENTE' },
    @{ Id = 'E3'; Hr = 88; Sys = 130; Dia = 85; Spo2 = 98; Temp = 38.2; Pain = 6; Gcs = 15; Complaint = 'Fever'; Expected = 'URGENTE_2' },
    @{ Id = 'E4'; Hr = 72; Sys = 120; Dia = 80; Spo2 = 99; Temp = 36.5; Pain = 2; Gcs = 15; Complaint = 'Cut wound'; Expected = 'NO_URGENTE' },
    @{ Id = 'E5'; Hr = 70; Sys = 115; Dia = 75; Spo2 = 99; Temp = 36.4; Pain = 0; Gcs = 15; Complaint = 'Renovación de recetario'; Expected = 'NO_URGENTE_2' },
    @{ Id = 'E6'; Hr = 35; Sys = 80; Dia = 50; Spo2 = 85; Temp = 35.9; Pain = 0; Gcs = 6; Complaint = 'Unconscious'; Expected = 'CRITICO' }
)
$patientIds = @{}
foreach ($case in $cases) {
    $body = PatientBody -Name "Patient $($case.Id) $suffix" -Hr $case.Hr -Sys $case.Sys -Dia $case.Dia -Spo2 $case.Spo2 -Temp $case.Temp -Pain $case.Pain -Gcs $case.Gcs -Complaint $case.Complaint
    $res = Invoke-Api -Method POST -Url "$triage/triage/pacientes" -Headers $triageHeaders -Body $body
    $actual = if ($res.Json) { $res.Json.nivel } else { $null }
    $patientIds[$case.Id] = $res.Json.id
    Check "RF-01 $($case.Id)" ($res.Status -eq 201 -and $actual -eq $case.Expected) "status=$($res.Status) level=$actual expected=$($case.Expected) id=$($patientIds[$case.Id])"
}

# RF-02 lifecycle on the NO_URGENTE patient
$lifeId = $patientIds['E4']
$get = Invoke-Api -Method GET -Url "$triage/triage/pacientes/$lifeId" -Headers $triageHeaders
Check 'RF-02 GET registered' ($get.Status -eq 200 -and $get.Json.estado -eq 'REGISTRADO') "status=$($get.Status) state=$($get.Json.estado)"
$inConsult = Invoke-Api -Method PATCH -Url "$triage/triage/pacientes/$lifeId/estado" -Headers $triageHeaders -Body @{ estado = 'EN_CONSULTA' }
Check 'RF-02 PATCH EN_CONSULTA' ($inConsult.Status -eq 200 -and $inConsult.Json.estado -eq 'EN_CONSULTA') "status=$($inConsult.Status) state=$($inConsult.Json.estado)"
$discharge = Invoke-Api -Method PATCH -Url "$triage/triage/pacientes/$lifeId/estado" -Headers $triageHeaders -Body @{ estado = 'ALTA' }
Check 'RF-02 PATCH ALTA' ($discharge.Status -eq 200 -and $discharge.Json.estado -eq 'ALTA') "status=$($discharge.Status) state=$($discharge.Json.estado)"
$invalid = Invoke-Api -Method PATCH -Url "$triage/triage/pacientes/$lifeId/estado" -Headers $triageHeaders -Body @{ estado = 'EN_CONSULTA' }
Check 'RF-02 invalid transition' ($invalid.Status -eq 409 -and $invalid.Json.code -eq 'INVALID_TRANSITION') "status=$($invalid.Status) code=$($invalid.Json.code)"
$missing = Invoke-Api -Method GET -Url "$triage/triage/pacientes/00000000-0000-4000-8000-000000000000" -Headers $triageHeaders
Check 'RF-02 unknown id' ($missing.Status -eq 404 -and $missing.Json.code -eq 'PATIENT_NOT_FOUND') "status=$($missing.Status) code=$($missing.Json.code)"

# RF-03/RF-06/RF-16: dedicated CRITICO registration carrying an explicit W3C trace context
$slaBody = PatientBody -Name "SLA patient $suffix" -Hr 130 -Sys 180 -Dia 110 -Spo2 97 -Temp 36.8 -Pain 8 -Gcs 15 -Complaint 'Chest pain'
$slaReg = Invoke-Api -Method POST -Url "$triage/triage/pacientes" -Headers $triageHeaders -Body $slaBody -TraceParent "00-$traceId-$spanId-01"
Check 'RF-16 POST with traceparent' ($slaReg.Status -eq 201 -and $slaReg.Json.nivel -eq 'CRITICO') "status=$($slaReg.Status) id=$($slaReg.Json.id) trace=$traceId"
$slaPatientId = $slaReg.Json.id

# The assistant consumes the event; this read also emits a span in the same trace.
Start-Sleep -Seconds 4
$urgency = Invoke-Api -Method GET -Url "$assistant/asistente/estado-urgencias" -Headers $assistantHeaders -TraceParent "00-$traceId-$spanId-01"
Check 'RF-04 urgency projection' ($urgency.Status -eq 200 -and $urgency.Json.totalActivos -ge 1) "status=$($urgency.Status) totalActivos=$($urgency.Json.totalActivos) CRITICO=$($urgency.Json.porNivel.CRITICO)"

# Poll until the 1-minute SLA escalates this patient.
$alert = $null
$deadline = (Get-Date).AddSeconds(180)
do {
    Start-Sleep -Seconds 5
    $alertsRes = Invoke-Api -Method GET -Url "$triage/triage/alertas" -Headers $triageHeaders
    $alert = @($alertsRes.Json) | Where-Object { $_.pacienteId -eq $slaPatientId } | Select-Object -First 1
} until ($alert -or (Get-Date) -gt $deadline)
Check 'RF-03 SLA escalation' ($null -ne $alert -and $alert.estado -eq 'PENDIENTE') "alert=$($alert.id) level=$($alert.nivel) sla=$($alert.slaMinutos) state=$($alert.estado)"

# SSE: start the stream, acknowledge the alert, and confirm the live event and heartbeat.
Set-Content -Path $sseCmd -Value "@echo off`r`ncurl.exe -N --max-time 45 -H `"X-API-Key: $triageKey`" -o `"$ssePath`" $triage/triage/alertas/stream" -Encoding ASCII
if (Test-Path $ssePath) { Remove-Item $ssePath -Force }
$sseProc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $sseCmd -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 2
$badAck = Invoke-Api -Method POST -Url "$triage/triage/alertas/$($alert.id)/reconocimiento" -Headers $triageHeaders -Body @{}
Check 'RF-06 invalid acknowledgement' ($badAck.Status -eq 400 -and $badAck.Json.code -eq 'TRG-004') "status=$($badAck.Status) code=$($badAck.Json.code)"
$ack = Invoke-Api -Method POST -Url "$triage/triage/alertas/$($alert.id)/reconocimiento" -Headers $triageHeaders -Body @{ responsable = 'dra-garcia' }
Check 'RF-06 acknowledgement' ($ack.Status -eq 200 -and $ack.Json.estado -eq 'RECONOCIDA') "status=$($ack.Status) state=$($ack.Json.estado) responsible=$($ack.Json.reconocidaPor)"
$ackAgain = Invoke-Api -Method POST -Url "$triage/triage/alertas/$($alert.id)/reconocimiento" -Headers $triageHeaders -Body @{ responsable = 'dra-garcia' }
Check 'RF-06 duplicate acknowledgement' ($ackAgain.Status -eq 409) "status=$($ackAgain.Status) code=$($ackAgain.Json.code)"

# Resolve while the stream is still open so the RESUELTA event is captured.
$resolvePatient = Invoke-Api -Method PATCH -Url "$triage/triage/pacientes/$slaPatientId/estado" -Headers $triageHeaders -Body @{ estado = 'EN_CONSULTA' }
# Let curl exit on its own so its buffered tail is flushed before the file is read.
if (-not $sseProc.WaitForExit(50000)) { Stop-Process -Id $sseProc.Id -Force; Start-Sleep -Seconds 1 }
$sseText = if (Test-Path $ssePath) { Get-Content $ssePath -Raw } else { '' }
$sseHasSnapshot = $sseText -match 'event:\s*alerta'
$sseHasLive = $sseText.Contains([string]$alert.id)
$sseHasHeartbeat = $sseText -match ':\s*latido'
Check 'RF-06 SSE snapshot/heartbeat' ($sseHasSnapshot -and $sseHasHeartbeat) "alert=$sseHasSnapshot heartbeat=$sseHasHeartbeat bytes=$($sseText.Length)"
Check 'RF-06 SSE live change' $sseHasLive "alertId=$($alert.id) present=$sseHasLive"

$alertsAfter = Invoke-Api -Method GET -Url "$triage/triage/alertas" -Headers $triageHeaders
$resolved = @($alertsAfter.Json) | Where-Object { $_.id -eq $alert.id } | Select-Object -First 1
$sseHasResolved = $sseText.Contains([string]$alert.id) -and ($sseText -match 'RESUELTA')
Check 'RF-06 alert resolved' ($resolvePatient.Status -eq 200 -and $sseHasResolved) "patient=$($resolvePatient.Status) alert_in_sse=$sseHasResolved active_list=$($resolved.estado)"

# RF-05 report
$from = (Get-Date).ToUniversalTime().AddDays(-1).ToString('yyyy-MM-ddTHH:mm:ssZ')
$to = (Get-Date).ToUniversalTime().AddDays(1).ToString('yyyy-MM-ddTHH:mm:ssZ')
$report = Invoke-Api -Method GET -Url "$triage/triage/reportes/operacion?desde=$from&hasta=$to" -Headers $triageHeaders
Check 'RF-05 valid report' ($report.Status -eq 200) "status=$($report.Status) body=$($report.Text.Substring(0, [Math]::Min(220, $report.Text.Length)))"
$badReport = Invoke-Api -Method GET -Url "$triage/triage/reportes/operacion" -Headers $triageHeaders
Check 'RF-05 invalid parameters' ($badReport.Status -eq 400 -and $badReport.Json.code -eq 'VALIDATION_ERROR') "status=$($badReport.Status) code=$($badReport.Json.code) field=$($badReport.Json.field)"

# RF-04 degraded assistant response
$consultHeaders = @{ 'X-API-Key' = $assistantKey; 'X-Correlation-Id' = "demo-$suffix" }
$consult = Invoke-Api -Method POST -Url "$assistant/asistente/consulta" -Headers $consultHeaders -Body @{ pregunta = 'Cuales son las medidas iniciales ante una crisis hipertensiva?' }
Check 'RF-04 degraded consultation' ($consult.Status -eq 503 -and $consult.Text -match 'LLM_UNAVAILABLE') "status=$($consult.Status) body=$($consult.Text.Substring(0, [Math]::Min(180, $consult.Text.Length)))"

# Auth negatives
$noKey = Invoke-Api -Method GET -Url "$triage/triage/alertas"
$wrongKey = Invoke-Api -Method GET -Url "$triage/triage/alertas" -Headers @{ 'X-API-Key' = 'wrong' }
$corrNo = $noKey.Headers['X-Correlation-Id']; if (-not $corrNo) { $corrNo = $noKey.Json.correlationId }
$corrWrong = $wrongKey.Headers['X-Correlation-Id']; if (-not $corrWrong) { $corrWrong = $wrongKey.Json.correlationId }
Check 'Auth without key' ($noKey.Status -eq 401 -and $noKey.Json.code -eq 'UNAUTHORIZED' -and $corrNo) "status=$($noKey.Status) correlation=$corrNo"
Check 'Auth invalid key' ($wrongKey.Status -eq 401 -and $wrongKey.Json.code -eq 'UNAUTHORIZED' -and $corrWrong) "status=$($wrongKey.Status) correlation=$corrWrong"

# NFR-07 metrics
$metricsT = Invoke-Api -Method GET -Url "$triage/actuator/prometheus" -Headers $triageHeaders
$metricsA = Invoke-Api -Method GET -Url "$assistant/actuator/prometheus" -Headers $assistantHeaders
$hasClassified = $metricsT.Text -match '(?m)^triage_patients_classified_total\{'
$hasEscalated = $metricsT.Text -match '(?m)^triage_patients_escalated_total\{'
$hasAck = $metricsT.Text -match '(?m)^triage_alerts_acknowledged_total\{'
$hasAssistant = $metricsA.Text -match '(?m)^assistant_(answers_total|llm_latency_seconds_bucket|urgency_events_total)\{'
Check 'NFR-07 triage metrics' ($metricsT.Status -eq 200 -and $hasClassified -and $hasEscalated -and $hasAck) "classified=$hasClassified escalated=$hasEscalated ack=$hasAck"
Check 'NFR-07 assistant metrics' ($metricsA.Status -eq 200 -and $hasAssistant) "series=$hasAssistant"

# Outbox -> Kafka with the explicit W3C header
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$outboxRaw = ''; for ($i = 0; $i -lt 6; $i++) { $outboxRaw = & kubectl --context $KubectlContext -n $Namespace exec local-db-0 -- env PGPASSWORD=local-dev-password psql -t -A -U triage_admin -d triage -c "SELECT count(*) FROM outbox_evento WHERE traceparent LIKE '%$traceId%';" 2>&1 | Out-String; if ($outboxRaw -match '(?m)^\s*[1-9]\d*\s*$') { break }; Start-Sleep -Seconds 2 }
$kafkaHeaders = & kubectl --context $KubectlContext -n $Namespace exec kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic triaje.eventos --from-beginning --property print.headers=true --max-messages 600 --timeout-ms 10000 2>&1 | Out-String
$ErrorActionPreference = $oldEap
$match = [regex]::Match($outboxRaw, '(?m)^\s*(\d+)\s*$')
$outboxCount = if ($match.Success) { $match.Groups[1].Value } else { '0' }
$kafkaHasTrace = $kafkaHeaders.Contains($traceId)
Check 'Outbox with traceparent' ([int]$outboxCount -ge 1) "rows=$outboxCount trace=$traceId"
Check 'Kafka header traceparent' $kafkaHasTrace "trace=$traceId present=$kafkaHasTrace"

# NFR-16 one trace in Jaeger
Start-Sleep -Seconds 8
$traceRes = Invoke-Api -Method GET -Url "$jaeger/api/traces/$traceId"
$traceText = $traceRes.Text
$hasTriage = $traceText.Contains('triage-service')
$hasAssistantTrace = $traceText.Contains('clinical-assistant-service')
Check 'NFR-16 shared trace' ($traceRes.Status -eq 200 -and $hasTriage -and $hasAssistantTrace) "status=$($traceRes.Status) triage=$hasTriage assistant=$hasAssistantTrace bytes=$($traceText.Length)"

# Summary
$failed = @($results | Where-Object { -not $_.Passed })
$passedCount = @($results | Where-Object { $_.Passed }).Count
$summary = "TOTAL=$($results.Count) PASS=$passedCount FAIL=$($failed.Count)"
Write-Output "=== $summary ==="
$lines = @("=== Local Kubernetes verification $(Get-Date -Format o) ===", $summary, '')
foreach ($r in $results) {
    $label = if ($r.Passed) { 'PASS' } else { 'FAIL' }
    $lines += ("{0} {1} :: {2}" -f $label, $r.Name, $r.Detail)
}
$lines += '', '--- SSE raw ---', $sseText, '--- Kafka sample ---', $kafkaHeaders
$lines | Out-File -FilePath $ReportPath -Encoding utf8
Write-Output "Report: $ReportPath"
if ($failed.Count -gt 0) { exit 1 }
