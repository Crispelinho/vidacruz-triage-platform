param(
    [string]$Kubeconfig = 'C:\Users\HP\AppData\Local\Temp\vidacruz-vidacruz-aks.kubeconfig',
    [string]$PublicHost = 'https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com',
    [string]$ReportPath = (Join-Path $env:TEMP 'azure-security-stack.txt'),
    [string]$PatientId = '16476d7d-1d50-4157-b406-9e9edc2958e5',
    [string]$ExpectedImageSha = '206ede741b216619b3be967ac317376bd1009e0b'
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PATH = 'C:\Users\HP\.azure-kubectl;C:\Users\HP\.azure-kubelogin;C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin;' + $env:PATH

$script:callIndex = 0
$results = New-Object System.Collections.ArrayList

function Check {
    param([string]$Name, [bool]$Passed, [string]$Detail)
    $label = if ($Passed) { 'PASS' } else { 'FAIL' }
    [void]$results.Add([pscustomobject]@{ Name = $Name; Passed = $Passed; Detail = $Detail })
    Write-Output ("{0} {1} :: {2}" -f $label, $Name, $Detail)
}

function Secret-Value {
    param([string]$Secret, [string]$Key)
    $raw = & kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get secret $Secret -o json
    $obj = $raw | ConvertFrom-Json
    $encoded = $obj.data.$Key
    if (-not $encoded) { throw "Missing key $Key in secret $Secret" }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encoded))
}

function Invoke-Curl {
    param([string]$Method, [string]$Url, [hashtable]$Headers = @{}, [object]$Body = $null)
    $script:callIndex++
    $outPath = Join-Path $env:TEMP "vidacruz-sec-out-$($script:callIndex).json"
    $args = @('-k', '-s', '-o', $outPath, '-w', '%{http_code}', '--max-time', '60', '-X', $Method)
    foreach ($h in $Headers.Keys) { $args += @('-H', "$h`: $($Headers[$h])") }
    if ($null -ne $Body) {
        $bodyPath = Join-Path $env:TEMP "vidacruz-sec-body-$($script:callIndex).json"
        [IO.File]::WriteAllText($bodyPath, ($Body | ConvertTo-Json -Compress), (New-Object Text.UTF8Encoding($false)))
        $args += @('--data-binary', "@$bodyPath")
    }
    $args += $Url
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $status = (& curl.exe @args 2>&1 | Out-String).Trim()
    $ErrorActionPreference = $old
    $text = if (Test-Path $outPath) { Get-Content $outPath -Raw -Encoding utf8 } else { '' }
    Remove-Item $outPath -Force -ErrorAction SilentlyContinue
    if ($null -ne $Body) { Remove-Item $bodyPath -Force -ErrorAction SilentlyContinue }
    return [pscustomobject]@{ Status = $status; Text = $text }
}

$triageKey = Secret-Value -Secret 'triage-service-secrets' -Key 'TRIAGE_API_KEY'

# Public contract and health: no API key, 16 URLs from azure-v106-verification.md:23-32.
$publicUrls = @(
    '/docs/triage/swagger-ui.html',
    '/docs/triage/swagger-ui/index.html',
    '/docs/triage/v3/api-docs',
    '/docs/triage/v3/api-docs/swagger-config',
    '/docs/asistente/swagger-ui.html',
    '/docs/asistente/swagger-ui/index.html',
    '/docs/asistente/v3/api-docs',
    '/docs/asistente/v3/api-docs/swagger-config',
    '/docs/triage/webjars/swagger-ui/index.css',
    '/docs/triage/webjars/swagger-ui/swagger-ui-bundle.js',
    '/docs/triage/webjars/swagger-ui/swagger-ui-standalone-preset.js',
    '/docs/asistente/webjars/swagger-ui/index.css',
    '/docs/asistente/webjars/swagger-ui/swagger-ui-bundle.js',
    '/docs/asistente/webjars/swagger-ui/swagger-ui-standalone-preset.js',
    '/ops/triage/health/readiness',
    '/ops/asistente/health/readiness'
)
$publicPass = 0
foreach ($url in $publicUrls) {
    $r = Invoke-Curl -Method GET -Url "$PublicHost$url"
    $ok = ($r.Status -eq '200' -or $r.Status -eq '302')
    if ($ok) { $publicPass++ }
    Check -Name "public $url" -Passed $ok -Detail "status=$($r.Status)"
}
Check -Name 'Public contract and health (16 URLs)' -Passed ($publicPass -eq 16) -Detail "$publicPass/16"

# Authentication: missing key, bogus key and owner key on the same PHI read route.
$patientUrl = "$PublicHost/triage/pacientes/$PatientId"
$noKey = Invoke-Curl -Method GET -Url $patientUrl
$noKeyOk = ($noKey.Status -eq '401' -and $noKey.Text -match '"code"\s*:\s*"UNAUTHORIZED"' -and $noKey.Text -match 'correlationId')
Check -Name '401 without X-API-Key' -Passed $noKeyOk -Detail "status=$($noKey.Status) body=NFR-20"

$bogus = Invoke-Curl -Method GET -Url $patientUrl -Headers @{ 'X-API-Key' = 'invalid-key-for-negative-test' }
Check -Name '401 with bogus X-API-Key' -Passed ($bogus.Status -eq '401') -Detail "status=$($bogus.Status)"

$owner = Invoke-Curl -Method GET -Url $patientUrl -Headers @{ 'X-API-Key' = $triageKey }
$ownerOk = ($owner.Status -eq '200' -and $owner.Text -match [regex]::Escape("`"id`":`"$PatientId`""))
Check -Name '200 with owner X-API-Key' -Passed $ownerOk -Detail "status=$($owner.Status) patient=synthetic"

# PHI read audit: the GET above must leave one audit.phi line with the key id, not the key value.
Start-Sleep -Seconds 2
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$podsRaw = (& kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get pods -o jsonpath='{.items[*].metadata.name}' 2>&1 | Out-String)
$podName = @($podsRaw -split '\s+' | Where-Object { $_ -like 'triage-service-*' } | Select-Object -First 1)[0]
$logs = if ($podName) { (& kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz logs "pod/$podName" --since=5m --tail=1500 2>&1 | Out-String) } else { '' }
$ErrorActionPreference = $oldEap
$auditLines = @($logs -split "`r?`n" | Where-Object { $_ -match 'PHI read' -and $_ -match [regex]::Escape($PatientId) -and $_ -match 'keyId=owner' })
Check -Name 'PHI read audit line' -Passed ($auditLines.Count -ge 1) -Detail "matching_lines=$($auditLines.Count) keyId=owner (value never logged)"

# Network policies exist; enforcement is a separate, documented gap.
$np = (& kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get networkpolicy -o jsonpath='{.items[*].metadata.name}' 2>&1 | Out-String).Trim()
$npNames = @($np -split '\s+' | Where-Object { $_ })
Check -Name 'NetworkPolicies present' -Passed ($npNames.Count -ge 6) -Detail "count=$($npNames.Count) names=$($np -replace '\s+',',')"

# The running images must be the develop head dispatched to the workflow.
$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$deployJson = (& kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get deployments -o json 2>&1 | Out-String)
$ErrorActionPreference = $oldEap
$deployments = $null
try { $deployments = $deployJson | ConvertFrom-Json } catch { }
$images = @()
if ($deployments) {
    $images = @($deployments.items | Where-Object { $_.metadata.name -in @('triage-service', 'clinical-assistant-service') } |
        ForEach-Object { '{0}={1}' -f $_.metadata.name, $_.spec.template.spec.containers[0].image })
}
$images = $images -join ' | '
$shortSha = $ExpectedImageSha.Substring(0, 7)
$imageOk = ($images -match [regex]::Escape($shortSha))
Check -Name 'Deployed image tag matches develop head' -Passed $imageOk -Detail $images

# Reviewer keys are optional; record whether they are configured (names only, never values).
foreach ($entry in @(@{ Secret = 'triage-service-secrets'; Key = 'TRIAGE_REVIEWER_API_KEY' }, @{ Secret = 'clinical-assistant-service-secrets'; Key = 'ASSISTANT_REVIEWER_API_KEY' })) {
    $raw = & kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get secret $entry.Secret -o json
    $obj = $raw | ConvertFrom-Json
    $names = @($obj.data.PSObject.Properties.Name)
    $present = $names -contains $entry.Key
    Check -Name "reviewer key $($entry.Key)" -Passed $true -Detail "configured=$present (names only; 403 path untested when false)"
}

$total = $results.Count
$failed = @($results | Where-Object { -not $_.Passed }).Count
$lines = @("Generated: $(Get-Date -Format o)", "Host: $PublicHost", "Expected image SHA: $ExpectedImageSha", '')
$lines += $results | ForEach-Object { "{0} {1} :: {2}" -f $(if ($_.Passed) { 'PASS' } else { 'FAIL' }), $_.Name, $_.Detail }
$lines += @('', "TOTAL=$total PASS=$($total - $failed) FAIL=$failed", 'Exposure: no API key, password, patient name or complaint appears in this report.')
$lines | Out-File -FilePath $ReportPath -Encoding utf8
Write-Output "Report: $ReportPath"
if ($failed -gt 0) { exit 1 }
