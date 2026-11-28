param(
    [string]$Kubeconfig = 'C:\Users\HP\AppData\Local\Temp\vidacruz-vidacruz-aks.kubeconfig',
    [string]$PublicHost = 'https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com',
    [string]$ReportPath = (Join-Path $env:TEMP 'vidacruz-azure-r6.txt')
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PATH = 'C:\Users\HP\.azure-kubectl;C:\Users\HP\.azure-kubelogin;C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin;' + $env:PATH
$script:curlIndex = 0

$results = New-Object System.Collections.ArrayList
function Check {
    param([string]$Name, [bool]$Passed, [string]$Detail)
    $label = if ($Passed) { 'PASS' } else { 'FAIL' }
    [void]$results.Add([pscustomobject]@{ Name = $Name; Passed = $Passed; Detail = $Detail })
    Write-Output ("{0} {1} :: {2}" -f $label, $Name, $Detail)
}

function Invoke-Api {
    param([string]$Method, [string]$Url, [hashtable]$Headers = @{}, [object]$Body = $null, [string]$TraceParent = $null)
    $script:curlIndex++
    $bodyFile = Join-Path $env:TEMP "vidacruz-azure-curl-body-$($script:curlIndex).txt"
    $jsonFile = $null
    $curlArgs = @('-k', '-s', '-o', $bodyFile, '-w', '%{http_code}', '--max-time', '40', '-X', $Method)
    foreach ($key in $Headers.Keys) { $curlArgs += @('-H', "$key`: $($Headers[$key])") }
    if ($TraceParent) { $curlArgs += @('-H', "traceparent`: $TraceParent") }
    if ($null -ne $Body) {
        $jsonFile = Join-Path $env:TEMP "vidacruz-azure-curl-json-$($script:curlIndex).json"
        $jsonText = $Body | ConvertTo-Json -Depth 12 -Compress
        [IO.File]::WriteAllText($jsonFile, $jsonText, (New-Object Text.UTF8Encoding($false)))
        $curlArgs += @('-H', 'Content-Type: application/json', '--data-binary', "@$jsonFile")
    }
    $curlArgs += $Url
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $statusText = & curl.exe @curlArgs 2>&1
    $ErrorActionPreference = $oldEap
    $text = if (Test-Path $bodyFile) { Get-Content $bodyFile -Raw -Encoding utf8 } else { '' }
    if (Test-Path $bodyFile) { Remove-Item $bodyFile -Force }
    if ($jsonFile -and (Test-Path $jsonFile)) { Remove-Item $jsonFile -Force }
    $status = 0
    [void][int]::TryParse(([string]$statusText).Trim(), [ref]$status)
    $json = $null
    try { $json = $text | ConvertFrom-Json } catch { }
    return [pscustomobject]@{ Status = $status; Text = $text; Json = $json }
}

function Secret-Value {
    param([string]$Secret, [string]$Key)
    $raw = & kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get secret $Secret -o json
    $obj = $raw | ConvertFrom-Json
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($obj.data.$Key))
}

$triageKey = Secret-Value -Secret 'triage-service-secrets' -Key 'TRIAGE_API_KEY'
$assistantKey = Secret-Value -Secret 'clinical-assistant-service-secrets' -Key 'ASSISTANT_API_KEY'
$triageHeaders = @{ 'X-API-Key' = $triageKey }
$assistantHeaders = @{ 'X-API-Key' = $assistantKey }
$traceId = '7c2e9b41d8a64f30b5e7c1a9f2d6b845'
$spanId = 'b2c3d4e5f6071829'
$suffix = Get-Random -Minimum 10000 -Maximum 99999
$ssePath = Join-Path $env:TEMP 'vidacruz-azure-r6-sse.txt'
$sseCmd = Join-Path $env:TEMP 'vidacruz-azure-r6-sse.cmd'

Write-Output "=== Azure R-06 verification $(Get-Date -Format o) ==="
$tHealth = Invoke-Api -Method GET -Url "$PublicHost/ops/triage/health/readiness"
$aHealth = Invoke-Api -Method GET -Url "$PublicHost/ops/asistente/health/readiness"
Check 'Health triage via /ops' ($tHealth.Status -eq 200) "status=$($tHealth.Status)"
Check 'Health assistant via /ops' ($aHealth.Status -eq 200) "status=$($aHealth.Status)"

$body = @{
    nombre_completo = "Azure R6 $suffix"
    edad = 55
    frecuencia_cardiaca = 130
    presion_sistolica = 180
    presion_diastolica = 110
    spo2 = 97
    temperatura = 36.8
    dolor = 8
    gcs = 15
    queja_principal = 'Chest pain'
}
$registration = Invoke-Api -Method POST -Url "$PublicHost/triage/pacientes" -Headers $triageHeaders -Body $body -TraceParent "00-$traceId-$spanId-01"
$patientId = if ($registration.Json) { $registration.Json.id } else { $null }
Check 'Critical registration with traceparent' ($registration.Status -eq 201 -and $registration.Json.nivel -eq 'CRITICO') "status=$($registration.Status) level=$($registration.Json.nivel) id=$patientId trace=$traceId"

$alert = $null
$deadline = (Get-Date).AddSeconds(540)
do {
    Start-Sleep -Seconds 10
    $alertsRes = Invoke-Api -Method GET -Url "$PublicHost/triage/alertas" -Headers $triageHeaders
    $alert = @($alertsRes.Json) | Where-Object { $_.pacienteId -eq $patientId } | Select-Object -First 1
} until ($alert -or (Get-Date) -gt $deadline)
Check 'RF-03 SLA escalation on Azure' ($null -ne $alert -and $alert.estado -eq 'PENDIENTE') "status=$($alert.estado) level=$($alert.nivel) sla=$($alert.slaMinutos) id=$($alert.id)"

if ($alert) {
    $env:AZ_TRIAGE_API_KEY = $triageKey
    Set-Content -Path $sseCmd -Value "@echo off`r`ncurl.exe -k -N --max-time 45 -H `"X-API-Key: %AZ_TRIAGE_API_KEY%`" -o `"$ssePath`" $PublicHost/triage/alertas/stream" -Encoding ASCII
    if (Test-Path $ssePath) { Remove-Item $ssePath -Force }
    $sseProc = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', $sseCmd -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 3

    $badAck = Invoke-Api -Method POST -Url "$PublicHost/triage/alertas/$($alert.id)/reconocimiento" -Headers $triageHeaders -Body @{}
    Check 'RF-06 invalid acknowledgement' ($badAck.Status -eq 400 -and $badAck.Json.code -eq 'TRG-004') "status=$($badAck.Status) code=$($badAck.Json.code)"
    $ack = Invoke-Api -Method POST -Url "$PublicHost/triage/alertas/$($alert.id)/reconocimiento" -Headers $triageHeaders -Body @{ responsable = 'dra-garcia' }
    Check 'RF-06 acknowledgement' ($ack.Status -eq 200 -and $ack.Json.estado -eq 'RECONOCIDA') "status=$($ack.Status) state=$($ack.Json.estado)"
    $ackAgain = Invoke-Api -Method POST -Url "$PublicHost/triage/alertas/$($alert.id)/reconocimiento" -Headers $triageHeaders -Body @{ responsable = 'dra-garcia' }
    Check 'RF-06 duplicate acknowledgement' ($ackAgain.Status -eq 409) "status=$($ackAgain.Status) code=$($ackAgain.Json.code)"

    $resolve = Invoke-Api -Method PATCH -Url "$PublicHost/triage/pacientes/$patientId/estado" -Headers $triageHeaders -Body @{ estado = 'EN_CONSULTA' }
    Start-Sleep -Seconds 18
    if (-not $sseProc.HasExited) { Stop-Process -Id $sseProc.Id -Force }
    Start-Sleep -Seconds 1
    $sse = if (Test-Path $ssePath) { Get-Content $ssePath -Raw } else { '' }
    $hasSnapshot = $sse -match 'event:\s*alerta'
    $hasHeartbeat = $sse -match ':\s*latido'
    $hasLive = $sse.Contains([string]$alert.id)
    $hasResolved = $sse -match 'RESUELTA'
    Check 'RF-06 SSE snapshot + heartbeat' ($hasSnapshot -and $hasHeartbeat) "snapshot=$hasSnapshot heartbeat=$hasHeartbeat bytes=$($sse.Length)"
    Check 'RF-06 SSE live alert + resolved' ($hasLive -and $hasResolved) "alertId=$($alert.id) live=$hasLive resolved=$hasResolved"
    Check 'RF-06 patient transition to EN_CONSULTA' ($resolve.Status -eq 200 -and $resolve.Json.estado -eq 'EN_CONSULTA') "status=$($resolve.Status) state=$($resolve.Json.estado)"
} else {
    Check 'RF-06 SSE snapshot + heartbeat' $false 'no alert was escalated; stream not opened'
    Check 'RF-06 SSE live alert + resolved' $false 'no alert was escalated; stream not opened'
}

$ragRuns = @()
for ($i = 1; $i -le 5; $i++) {
    $rag = Invoke-Api -Method POST -Url "$PublicHost/asistente/consulta" -Headers $assistantHeaders -Body @{ pregunta = '¿Cuáles son las medidas iniciales ante una crisis hipertensiva?' }
    $sources = @()
    if ($rag.Json -and $rag.Json.fuentes) { $sources = @($rag.Json.fuentes | ForEach-Object { if ($_ -is [string]) { $_ } else { $_.protocolo } }) }
    $hasSource = @($sources | Where-Object { $_ -like '*PROTO-001*' }).Count -ge 1
    $hasAnswer = $rag.Json -and -not [string]::IsNullOrWhiteSpace([string]$rag.Json.respuesta)
    $ragRuns += [pscustomobject]@{ Run = $i; Status = $rag.Status; HasSource = $hasSource; HasAnswer = [bool]$hasAnswer; Sources = ($sources -join ',') }
    Start-Sleep -Seconds 1
}
$ragPass = @($ragRuns | Where-Object { $_.Status -eq 200 -and $_.HasSource -and $_.HasAnswer }).Count
Check 'RAG chat 5/5 real-provider smoke (PROTO-001)' ($ragPass -eq 5) "pass=$ragPass/5 runs=$(($ragRuns | ForEach-Object { "$($_.Run):$($_.Status)/$($_.HasSource)" }) -join ' ')"

$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$kafka = & kubectl --kubeconfig $Kubeconfig --request-timeout=120s -n vidacruz exec kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic triaje.eventos --from-beginning --property print.headers=true --max-messages 300 --timeout-ms 20000 2>&1 | Out-String
$ErrorActionPreference = $oldEap
Check 'Kafka consumer carries traceparent' $kafka.Contains($traceId) "trace=$traceId present=$($kafka.Contains($traceId))"

$jaeger = & kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get svc -A 2>&1 | Out-String
$jaegerPresent = $jaeger -match '(?i)jaeger'
Check 'Jaeger in Azure (expected absent)' (-not $jaegerPresent) "present=$jaegerPresent; shared-trace evidence remains local-only"

$failed = @($results | Where-Object { -not $_.Passed }).Count
$passed = @($results | Where-Object { $_.Passed }).Count
$summary = "TOTAL=$($results.Count) PASS=$passed FAIL=$failed"
Write-Output "=== $summary ==="
$lines = @("=== Azure R-06 verification $(Get-Date -Format o) ===", "Target: $PublicHost", $summary, '')
foreach ($r in $results) {
    $label = if ($r.Passed) { 'PASS' } else { 'FAIL' }
    $lines += ("{0} {1} :: {2}" -f $label, $r.Name, $r.Detail)
}
$lines += '', '--- SSE raw ---', $sse, '--- Kafka excerpt ---', $kafka
$lines | Out-File -FilePath $ReportPath -Encoding utf8
Write-Output "Report: $ReportPath"
if ($failed -gt 0) { exit 1 }
