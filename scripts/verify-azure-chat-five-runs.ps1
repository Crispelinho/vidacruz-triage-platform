param(
    [string]$Kubeconfig = 'C:\Users\HP\AppData\Local\Temp\vidacruz-vidacruz-aks.kubeconfig',
    [string]$PublicHost = 'https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com',
    [string]$ReportPath = 'C:\Users\HP\AppData\Local\Temp\opencode\azure-chat-five-runs.txt'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PATH = 'C:\Users\HP\.azure-kubectl;C:\Users\HP\.azure-kubelogin;C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin;' + $env:PATH

function Secret-Value {
    param([string]$Secret, [string]$Key)
    $raw = & kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get secret $Secret -o json
    $obj = $raw | ConvertFrom-Json
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($obj.data.$Key))
}

$assistantKey = Secret-Value -Secret 'clinical-assistant-service-secrets' -Key 'ASSISTANT_API_KEY'

$script:index = 0
function Invoke-Chat {
    param([string]$Question)
    $script:index++
    $bodyPath = Join-Path $env:TEMP "vidacruz-chat-body-$($script:index).json"
    $outPath = Join-Path $env:TEMP "vidacruz-chat-out-$($script:index).json"
    [IO.File]::WriteAllText($bodyPath, (@{ pregunta = $Question } | ConvertTo-Json -Compress), (New-Object Text.UTF8Encoding($false)))
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $status = & curl.exe -k -s -o $outPath -w '%{http_code}' --max-time 90 -X POST `
        -H "X-API-Key: $assistantKey" -H 'Content-Type: application/json' `
        --data-binary "@$bodyPath" "$PublicHost/asistente/consulta" 2>&1
    $ErrorActionPreference = $old
    $text = if (Test-Path $outPath) { Get-Content $outPath -Raw -Encoding utf8 } else { '' }
    Remove-Item $bodyPath, $outPath -Force -ErrorAction SilentlyContinue
    $json = $null
    try { $json = $text | ConvertFrom-Json } catch { }
    $statusText = ([string]$status).Trim()
    for ($attempt = 0; $statusText -eq '503' -and $attempt -lt 3; $attempt++) {
        Start-Sleep -Seconds (3 * ($attempt + 1))
        $script:index++
        $bodyPath = Join-Path $env:TEMP "vidacruz-chat-body-$($script:index).json"
        $outPath = Join-Path $env:TEMP "vidacruz-chat-out-$($script:index).json"
        [IO.File]::WriteAllText($bodyPath, (@{ pregunta = $Question } | ConvertTo-Json -Compress), (New-Object Text.UTF8Encoding($false)))
        $oldEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $status = & curl.exe -k -s -o $outPath -w '%{http_code}' --max-time 90 -X POST `
            -H "X-API-Key: $assistantKey" -H 'Content-Type: application/json' `
            --data-binary "@$bodyPath" "$PublicHost/asistente/consulta" 2>&1
        $ErrorActionPreference = $oldEap
        $statusText = ([string]$status).Trim()
        $text = if (Test-Path $outPath) { Get-Content $outPath -Raw -Encoding utf8 } else { '' }
        Remove-Item $bodyPath, $outPath -Force -ErrorAction SilentlyContinue
        $json = $null
        try { $json = $text | ConvertFrom-Json } catch { }
    }
    return [pscustomobject]@{ Status = $statusText; Json = $json }
}

$probes = @(
    @{ Name = 'PROTO-001'; Expected = 'PROTO-001'; Question = '¿Cuáles son las medidas iniciales ante una crisis hipertensiva?' },
    @{ Name = 'PROTO-002'; Expected = 'PROTO-002'; Question = '¿Cuándo se administra el primer antibiótico en sepsis?' },
    @{ Name = 'PROTO-003 leve-4'; Expected = 'PROTO-003'; Question = 'Deshidratación pediatrica con perdida de peso del 4 % sin vomito, ¿como se clasifica y rehidrata?' },
    @{ Name = 'PROTO-003 moderada-6-5'; Expected = 'PROTO-003'; Question = 'Deshidratación pediatrica con perdida de peso del 6,5 % sin vomito, ¿como se clasifica y rehidrata?' },
    @{ Name = 'PROTO-003 severa-9'; Expected = 'PROTO-003'; Question = 'Deshidratación pediatrica con perdida de peso del 9 %, ¿como se clasifica y rehidrata?' },
    @{ Name = 'PROTO-003 alterado-4'; Expected = 'PROTO-003'; Question = 'Deshidratación pediatrica con perdida de peso del 4 % y alteracion del estado mental, ¿como se clasifica y rehidrata?' },
    @{ Name = 'negative diabetes'; Expected = 'NONE'; Question = '¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?' }
)

$results = New-Object System.Collections.ArrayList
$lines = New-Object System.Collections.ArrayList
[void]$lines.Add("=== Azure chat five-run gate $(Get-Date -Format o) ===")
[void]$lines.Add("Host: $PublicHost; target: main 38d9d77")

foreach ($probe in $probes) {
    $passes = 0
    $detail = @()
    for ($run = 1; $run -le 5; $run++) {
        $res = Invoke-Chat -Question $probe.Question
        $sources = @()
        if ($res.Json -and $res.Json.fuentes) {
            $sources = @($res.Json.fuentes | ForEach-Object { if ($_ -is [string]) { $_ } else { [string]$_.protocolo } })
        }
        $answer = if ($res.Json) { [string]$res.Json.respuesta } else { '' }
        $model = if ($res.Json) { [string]$res.Json.modelo_usado } else { '' }
        if ($probe.Expected -eq 'NONE') {
            $ok = ($res.Status -eq '200' -and $sources.Count -eq 0 -and $answer.Trim() -eq 'no existe protocolo disponible' -and $model -eq 'none')
            $detail += "$run`:$($res.Status)/fixed=$ok"
        } else {
            $hasSource = @($sources | Where-Object { $_ -like "*$($probe.Expected)*" }).Count -ge 1
            $ok = ($res.Status -eq '200' -and $hasSource -and -not [string]::IsNullOrWhiteSpace($answer) -and $model -ne 'none')
            $detail += "$run`:$($res.Status)/src=$hasSource/model=$model"
        }
        if ($ok) { $passes++ }
        Start-Sleep -Seconds 2
    }
    [void]$results.Add([pscustomobject]@{ Name = $probe.Name; Passes = $passes })
    [void]$lines.Add("PASS $($probe.Name) :: $passes/5 runs=$($detail -join ' ')")
    Write-Output "$($probe.Name): $passes/5"
}

$failed = @($results | Where-Object { $_.Passes -ne 5 })
$summary = "TOTAL=$($results.Count) PASS=$($results.Count - $failed.Count) FAIL=$($failed.Count)"
[void]$lines.Insert(1, $summary)
[void]$lines.Add('')
[void]$lines.Add('No credential values are stored in this report.')
$lines | Out-File -FilePath $ReportPath -Encoding utf8
Write-Output $summary
Write-Output "Report: $ReportPath"
if ($failed.Count -gt 0) { exit 1 }
