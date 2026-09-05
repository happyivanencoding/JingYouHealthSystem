$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$requiredUserEnvironment = @(
    'JINGYOU_CF_TEAM_DOMAIN',
    'JINGYOU_CF_AUD'
)
foreach ($name in $requiredUserEnvironment) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if (-not $value) {
        throw "$name is not configured in the current Windows user environment."
    }
    Set-Item -Path "Env:$name" -Value $value
}

$env:PYTHONUTF8 = '1'
$python = Join-Path $Root '.venv\Scripts\python.exe'
if (-not (Test-Path $python)) { throw "Python environment not found: $python" }

$logDir = Join-Path $Root 'data\app'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logPath = Join-Path $logDir 'backend.log'
"[$(Get-Date -Format o)] Starting JingYou Health backend" | Add-Content -Path $logPath -Encoding UTF8

& $python -m uvicorn server.app:app --host 127.0.0.1 --port 8788 2>&1 |
    Tee-Object -FilePath $logPath -Append
$exitCode = $LASTEXITCODE
"[$(Get-Date -Format o)] Backend exited with code $exitCode" | Add-Content -Path $logPath -Encoding UTF8
exit $exitCode
