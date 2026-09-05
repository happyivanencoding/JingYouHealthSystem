param(
  [Parameter(Mandatory=$true)][string]$PromptPath,
  [Parameter(Mandatory=$true)][string]$OutputPath,
  [Parameter(Mandatory=$true)][string]$Cwd,
  [string]$Model = 'gpt-5.6-sol',
  [ValidateSet('low','medium','high','xhigh','max','ultra')][string]$Reasoning = 'medium',
  [ValidateSet('read-only','agent','agent-full-access')][string]$Mode = 'read-only',
  [int]$TimeoutMinutes = 5
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$localAppData = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { [Environment]::GetFolderPath('LocalApplicationData') }
$runtime = Join-Path $localAppData 'AgentDock'
$uri = 'http://127.0.0.1:8766/mcp'
$encrypted = [IO.File]::ReadAllText((Join-Path $runtime 'auth-token.dpapi'), [Text.Encoding]::UTF8).Trim()
$plain = [System.Security.Cryptography.ProtectedData]::Unprotect(
  [Convert]::FromBase64String($encrypted),
  [Text.Encoding]::UTF8.GetBytes('agentdock.startup.v1'),
  [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
$token = [Text.Encoding]::UTF8.GetString($plain)
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/json, text/event-stream' }
$rpcId = 0

function Invoke-Rpc([string]$method, $params) {
  $script:rpcId++
  $body = @{ jsonrpc = '2.0'; id = $script:rpcId; method = $method; params = $params } | ConvertTo-Json -Depth 50 -Compress
  $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -Method Post -Headers $script:headers -ContentType 'application/json' -Body $body
  if ($response.Headers['Mcp-Session-Id']) { $script:headers['Mcp-Session-Id'] = $response.Headers['Mcp-Session-Id'] }
  if ($response.Content -match '^event:') {
    $json = (($response.Content -split "`n") | Where-Object { $_ -like 'data:*' } | Select-Object -First 1).Substring(5).Trim()
  } else {
    $json = $response.Content
  }
  return ($json | ConvertFrom-Json)
}

function Call-Tool([string]$name, $toolArgs) {
  $response = Invoke-Rpc 'tools/call' @{ name = $name; arguments = $toolArgs }
  if ($response.error) { throw ($response.error | ConvertTo-Json -Depth 10) }
  if ($response.result.structuredContent) { return $response.result.structuredContent }
  $text = ($response.result.content | Where-Object { $_.type -eq 'text' } | Select-Object -First 1).text
  if ($text) { return ($text | ConvertFrom-Json) }
  return $null
}

Invoke-Rpc 'initialize' @{ protocolVersion = '2025-11-25'; capabilities = @{}; clientInfo = @{ name = 'jingyou-health'; version = '1' } } | Out-Null
$initialized = @{ jsonrpc = '2.0'; method = 'notifications/initialized'; params = @{} } | ConvertTo-Json -Compress
Invoke-WebRequest -UseBasicParsing -Uri $uri -Method Post -Headers $headers -ContentType 'application/json' -Body $initialized | Out-Null

$session = Call-Tool 'acp_session' @{ action = 'new'; cwd = $Cwd }
$sessionId = $session.session.id
if (-not $sessionId) { $sessionId = $session.session_id }
Call-Tool 'acp_session' @{ action = 'set_config'; session_id = $sessionId; config_id = 'model'; config_value = $Model } | Out-Null
Call-Tool 'acp_session' @{ action = 'set_config'; session_id = $sessionId; config_id = 'reasoning_effort'; config_value = $Reasoning } | Out-Null
Call-Tool 'acp_session' @{ action = 'set_mode'; session_id = $sessionId; mode_id = $Mode } | Out-Null

$prompt = [IO.File]::ReadAllText($PromptPath, [Text.Encoding]::UTF8)
$started = Call-Tool 'acp_prompt' @{ action = 'start'; session_id = $sessionId; text = $prompt }
$runId = $started.run_id
$sequence = 0
$chunks = New-Object System.Collections.Generic.List[string]
$finalChunks = New-Object System.Collections.Generic.List[string]
$status = 'running'
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)

while ((Get-Date) -lt $deadline -and $status -in @('running', 'queued')) {
  $events = Call-Tool 'acp_prompt' @{ action = 'events'; run_id = $runId; after_seq = $sequence; limit = 200; wait_ms = 25000 }
  if ($null -ne $events.next_seq) { $sequence = [int]$events.next_seq }
  $status = [string]$events.status
  foreach ($event in @($events.events)) {
    if ($event.type -eq 'agent_message_chunk' -and $event.update.content.type -eq 'text') {
      $text = [string]$event.update.content.text
      [void]$chunks.Add($text)
      if ($event.update._meta.codex.phase -eq 'final_answer') {
        [void]$finalChunks.Add($text)
      }
    }
  }
}

if ($status -in @('running', 'queued')) {
  Call-Tool 'acp_prompt' @{ action = 'cancel'; run_id = $runId; session_id = $sessionId } | Out-Null
  throw 'ACP health-agent turn timed out.'
}
if ($chunks.Count -eq 0) { throw "ACP health-agent returned no text. Status: $status" }
$selectedChunks = if ($finalChunks.Count -gt 0) { $finalChunks } else { $chunks }
$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) { New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null }
[IO.File]::WriteAllText($OutputPath, ($selectedChunks -join ''), [Text.Encoding]::UTF8)
try { Call-Tool 'acp_session' @{ action = 'close'; session_id = $sessionId } | Out-Null } catch {}
Write-Output (@{ model = $Model; reasoning = $Reasoning; mode = $Mode; status = $status; output_path = $OutputPath } | ConvertTo-Json -Compress)
