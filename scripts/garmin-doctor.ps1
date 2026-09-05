param(
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
& $cli doctor --format json
exit $LASTEXITCODE
