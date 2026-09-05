param(
  [string]$Profile,
  [string]$UserId
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$cli = Join-Path $root ".venv\Scripts\garmin-cli.exe"
if (!(Test-Path $cli)) { throw "Garmin CLI not installed. Run: uv sync" }

if (-not $UserId -and $Profile) {
  $profilesFile = Join-Path $root "data\app\profiles.json"
  if (!(Test-Path $profilesFile)) { throw "Profiles file missing: $profilesFile" }
  $profiles = Get-Content $profilesFile -Raw | ConvertFrom-Json
  $entry = $profiles.PSObject.Properties | Where-Object { $_.Name -ieq $Profile } | Select-Object -First 1
  if (-not $entry) { throw "Unknown profile: $Profile" }
  $UserId = $entry.Value.user_id
}
if (-not $UserId) {
  $ownerFile = Join-Path $root "data\app\owner.json"
  if (!(Test-Path $ownerFile)) { throw "Owner profile missing: $ownerFile" }
  $UserId = (Get-Content $ownerFile -Raw | ConvertFrom-Json).user_id
}
$env:GARMIN_HOME = Join-Path $root ("data\users\{0}\garmin" -f $UserId)
New-Item -ItemType Directory -Force -Path $env:GARMIN_HOME | Out-Null

Write-Host "Garmin profile: $UserId"
Write-Host "Token home: $env:GARMIN_HOME"
& $cli login
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Garmin session saved. Running live status check..."
& $cli login status
