param(
  [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
  [string]$UserId
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$cli = Join-Path $root ".venv\Scripts\garmin-cli.exe"
if (!(Test-Path $cli)) { throw "Garmin CLI not installed. Run: uv sync" }
if (-not $UserId) {
  $ownerFile = Join-Path $root "data\app\owner.json"
  if (!(Test-Path $ownerFile)) { throw "Owner profile missing: $ownerFile" }
  $UserId = (Get-Content $ownerFile -Raw | ConvertFrom-Json).user_id
}
$env:GARMIN_HOME = Join-Path $root ("data\users\{0}\garmin" -f $UserId)

$checks = @(
  @("HRV", @("--json", "health", "hrv", "--date", $Date)),
  @("Sleep", @("--json", "health", "sleep", "--date", $Date)),
  @("Resting HR", @("--json", "health", "resting-hr", "--date", $Date)),
  @("Stress", @("--json", "health", "stress", "--date", $Date)),
  @("Body Battery", @("--json", "health", "body-battery", "--date", $Date)),
  @("Training Readiness", @("--json", "health", "readiness", "--date", $Date)),
  @("Training Status", @("--json", "health", "status", "--date", $Date))
)

$failed = $false
foreach ($check in $checks) {
  $name = $check[0]
  $args = $check[1]
  Write-Host "`n=== $name ==="
  & $cli @args
  if ($LASTEXITCODE -ne 0) { $failed = $true }
}
if ($failed) { exit 1 }
