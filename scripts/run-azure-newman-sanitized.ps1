param(
    [string]$Kubeconfig = 'C:\Users\HP\AppData\Local\Temp\vidacruz-vidacruz-aks.kubeconfig',
    [string]$Collection = (Join-Path (Split-Path $PSScriptRoot -Parent) 'docs/audits/20261128/VidaCruz.azure-no-secrets.postman_collection.json'),
    [string]$JsonReport = (Join-Path $env:TEMP 'newman-develop-206ede7.json'),
    [string]$LogPath = (Join-Path $env:TEMP 'newman-develop-206ede7.log'),
    [string]$SummaryPath = (Join-Path $env:TEMP 'newman-develop-206ede7-summary.txt')
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PATH = 'C:\Users\HP\.azure-kubectl;C:\Users\HP\.azure-kubelogin;C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin;' + $env:PATH

function Secret-Value {
    param([string]$Secret, [string]$Key)
    $raw = & kubectl --kubeconfig $Kubeconfig --request-timeout=90s -n vidacruz get secret $Secret -o json
    $obj = $raw | ConvertFrom-Json
    $encoded = $obj.data.$Key
    if (-not $encoded) { throw "Missing key $Key in secret $Secret" }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encoded))
}

# Credentials stay in process memory; only the raw Newman JSON (kept in TEMP, never committed) can contain them.
$triageKey = Secret-Value -Secret 'triage-service-secrets' -Key 'TRIAGE_API_KEY'
$assistantKey = Secret-Value -Secret 'clinical-assistant-service-secrets' -Key 'ASSISTANT_API_KEY'

$args = @('newman', 'run', $Collection,
    '--env-var', "triageApiKey=$triageKey",
    '--env-var', "asistenteApiKey=$assistantKey",
    '--reporters', 'cli,json',
    '--reporter-json-export', $JsonReport)

$oldEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& npx.cmd @args 2>&1 | Tee-Object -FilePath $LogPath | Out-String
$exitCode = $LASTEXITCODE
$ErrorActionPreference = $oldEap

$run = $null
try { $run = (Get-Content $JsonReport -Raw -Encoding utf8 | ConvertFrom-Json).run } catch { }
if (-not $run) { throw "No parseable Newman report at $JsonReport (exit=$exitCode)" }

$requests = $run.stats.requests
$assertions = $run.stats.assertions
$ok = ($requests.failed -eq 0 -and $assertions.failed -eq 0)
$lines = @(
    "Generated: $(Get-Date -Format o)",
    'Target: Azure develop 206ede7 (deploy run 46149735968)',
    "Requests: total=$($requests.total) failed=$($requests.failed) pending=$($requests.pending)",
    "Assertions: total=$($assertions.total) failed=$($assertions.failed) pending=$($assertions.pending)",
    "Result: $(if ($ok) { 'PASS' } else { 'FAIL' })",
    'Raw Newman JSON stays in TEMP and is not committed; this summary contains no credentials.'
)
$lines | Out-File -FilePath $SummaryPath -Encoding utf8
$lines | ForEach-Object { Write-Output $_ }
if (-not $ok) { exit 1 }
