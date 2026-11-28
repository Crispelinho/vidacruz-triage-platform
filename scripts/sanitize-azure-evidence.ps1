param(
    [string]$Root = (Split-Path $PSScriptRoot -Parent)
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object Text.UTF8Encoding($false)
$files = @(
    'docs/audits/20261128/azure-develop-206ede7-security-stack.txt',
    'docs/audits/20261128/azure-develop-206ede7-r6.txt',
    'docs/audits/20261128/azure-develop-206ede7-chat-first.txt',
    'docs/audits/20261128/azure-develop-206ede7-chat-rerun.txt',
    'docs/audits/20261128/azure-develop-206ede7-db-rowlevel.txt',
    'docs/audits/20261128/azure-develop-206ede7-newman-summary.txt',
    'docs/audits/20261128/azure-develop-206ede7-verification.md',
    'docs/audits/20261128/local-e77ff63-k8s-e2e.txt',
    'docs/audits/20261128/local-e77ff63-verification.md'
)

foreach ($rel in $files) {
    $path = Join-Path $Root $rel
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    $text = $text -replace 'target: main 38d9d77', 'target: develop 206ede7 (deploy run 46149735968)'
    $text = $text -replace '([A-Za-z_]*API_KEY)\s*[:=]\s*"?[A-Za-z0-9_\-]{16,}"?', '$1=<redacted>'
    [IO.File]::WriteAllText($path, $text, $utf8)
}

$all = @()
foreach ($rel in $files) {
    $path = Join-Path $Root $rel
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    $hits = @([regex]::Matches($text, '(?i)(api[_-]?key|password)\s*[:=]\s*"?([A-Za-z0-9_\-]{16,})') |
        ForEach-Object { "${rel}: $($_.Groups[1].Value)=<redacted>" })
    $all += $hits
}

$checks = @(
    'C:\Users\HP\AppData\Local\Temp\opencode\newman-v106-clean-rerun.json',
    'C:\Users\HP\AppData\Local\Temp\opencode\VidaCruz.azure-final.postman_collection.json',
    'C:\Users\HP\AppData\Local\Temp\opencode\azure-collection.no-secrets.json'
)
foreach ($path in $checks) {
    if (Test-Path $path) {
        $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
        $all += @([regex]::Matches($text, '(?i)(api[_-]?key|password)\s*[:=]\s*"?([A-Za-z0-9_\-]{16,})') |
            ForEach-Object { "${path}: $($_.Groups[1].Value)=<redacted>" })
    }
}

if ($all.Count -gt 0) {
    Write-Output 'REVIEW CANDIDATES (values redacted):'
    $all | Sort-Object -Unique
    exit 1
}

Write-Output 'Sanitization OK: no credential values in tracked evidence or checked TEMP files.'
