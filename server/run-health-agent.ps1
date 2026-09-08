param(
  [Parameter(Mandatory=$true)][string]$Cwd,
  [string]$Model = 'gpt-5.6-sol',
  [ValidateSet('low','medium','high')][string]$Reasoning = 'medium',
  [int]$TimeoutMinutes = 4
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security

$prompt = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($prompt)) { throw 'Transport prompt is empty' }
if (-not (Test-Path -LiteralPath $Cwd -PathType Container)) { throw "Neutral transport cwd does not exist: $Cwd" }

$runtime = Join-Path $env:LOCALAPPDATA 'AgentDock'
$serverUrl = [IO.File]::ReadAllText((Join-Path $runtime 'server-url.txt'), [Text.Encoding]::UTF8).Trim().TrimEnd('/')
$enc = [IO.File]::ReadAllText((Join-Path $runtime 'auth-token.dpapi'), [Text.Encoding]::UTF8).Trim()
$plain = [Security.Cryptography.ProtectedData]::Unprotect(
  [Convert]::FromBase64String($enc),
  [Text.Encoding]::UTF8.GetBytes('agentdock.startup.v1'),
  [Security.Cryptography.DataProtectionScope]::CurrentUser
)
$token = [Text.Encoding]::UTF8.GetString($plain)
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/json, text/event-stream' }
$nextId = 0

function Call-Rpc([string]$method, [object]$params, [switch]$notification) {
  if ($notification) {
    $body = @{ jsonrpc='2.0'; method=$method; params=$params } | ConvertTo-Json -Depth 50 -Compress
  } else {
    $script:nextId++
    $body = @{ jsonrpc='2.0'; id=$script:nextId; method=$method; params=$params } | ConvertTo-Json -Depth 50 -Compress
  }
  $response = Invoke-WebRequest -UseBasicParsing -Uri "$serverUrl/mcp" -Method Post -Headers $script:headers -ContentType 'application/json' -Body $body
  if ($response.Headers['Mcp-Session-Id']) { $script:headers['Mcp-Session-Id'] = $response.Headers['Mcp-Session-Id'] }
  if ($notification) { return $null }
  $content = $response.Content
  if ($content -match '^event:') {
    $line = ($content -split "`n" | Where-Object { $_ -like 'data:*' } | Select-Object -First 1)
    if ($line) { $content = $line.Substring(5).Trim() }
  }
  $rpc = $content | ConvertFrom-Json
  if ($rpc.error) { throw ($rpc.error | ConvertTo-Json -Depth 10 -Compress) }
  return $rpc.result
}

function Call-Tool([string]$name, [hashtable]$arguments) {
  $result = Call-Rpc 'tools/call' @{ name=$name; arguments=$arguments }
  if ($result.structuredContent) { return $result.structuredContent }
  $text = ($result.content | Where-Object { $_.type -eq 'text' } | Select-Object -First 1).text
  if (-not $text) { return @{} }
  return $text | ConvertFrom-Json
}

Call-Rpc 'initialize' @{ protocolVersion='2025-11-25'; capabilities=@{}; clientInfo=@{name='jingyou-runtime-model-transport';version='1'} } | Out-Null
Call-Rpc 'notifications/initialized' @{} -notification | Out-Null

$session = Call-Tool 'acp_session' @{ action='new'; cwd=$Cwd; additional_directories=@() }
$sessionId = if ($session.session.id) { $session.session.id } else { $session.session_id }
if (-not $sessionId) { throw 'ACP session id missing' }
Call-Tool 'acp_session' @{ action='set_config'; session_id=$sessionId; config_id='model'; config_value=$Model } | Out-Null
Call-Tool 'acp_session' @{ action='set_config'; session_id=$sessionId; config_id='reasoning_effort'; config_value=$Reasoning } | Out-Null
Call-Tool 'acp_session' @{ action='set_mode'; session_id=$sessionId; mode_id='read-only' } | Out-Null

$runId = $null
try {
  $started = Call-Tool 'acp_prompt' @{ action='start'; session_id=$sessionId; text=$prompt }
  $runId = $started.run_id
  if (-not $runId) { throw ('ACP run id missing: ' + ($started | ConvertTo-Json -Depth 10 -Compress)) }

  $sequence = 0
  $status = if ($started.status) { [string]$started.status } else { 'running' }
  $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
  $finalChunks = New-Object System.Collections.Generic.List[string]
  $fallbackChunks = New-Object System.Collections.Generic.List[string]
  $eventTypes = New-Object System.Collections.Generic.List[string]
  $prohibitedEvents = New-Object System.Collections.Generic.List[string]

  while ((Get-Date) -lt $deadline -and $status -in @('running','queued')) {
    $events = Call-Tool 'acp_prompt' @{ action='events'; run_id=$runId; after_seq=$sequence; limit=200; wait_ms=25000 }
    if ($events.next_seq -ne $null) { $sequence = [int]$events.next_seq }
    if ($events.status) { $status = [string]$events.status }
    foreach ($event in @($events.events)) {
      $type = [string]$event.type
      if ($type) { $eventTypes.Add($type) }
      if ($type -match '(?i)(tool|command|exec|shell|terminal|browser|web|file|filesystem|mcp|computer|permission)') {
        $prohibitedEvents.Add($type)
      }
      if ($type -ne 'agent_message_chunk' -or $event.update.content.type -ne 'text') { continue }
      $text = [string]$event.update.content.text
      if (-not $text) { continue }
      $fallbackChunks.Add($text)
      if ($event.update._meta.codex.phase -eq 'final_answer') { $finalChunks.Add($text) }
    }
  }
  if ($status -in @('running','queued')) {
    Call-Tool 'acp_prompt' @{ action='cancel'; run_id=$runId; session_id=$sessionId } | Out-Null
    throw "ACP prompt timed out after $TimeoutMinutes minutes"
  }
  if ($status -ne 'completed') { throw "ACP prompt ended with status $status" }

  $interactionCount = $null
  try {
    $interactionState = Call-Tool 'acp_interaction' @{ action='list'; session_id=$sessionId; pending_only=$false }
    $interactionCount = if ($interactionState.interactions) { @($interactionState.interactions).Count } else { 0 }
  } catch {
    # Some AgentDock versions do not expose interaction history; event audit still detects permission events.
  }

  if ($prohibitedEvents.Count -gt 0) {
    throw ('Transport-only invariant violated by ACP event type(s): ' + (($prohibitedEvents | Sort-Object -Unique) -join ', '))
  }
  if ($interactionCount -ne $null -and $interactionCount -gt 0) { throw "Transport-only invariant violated by $interactionCount ACP permission interaction(s)" }

  $answer = if ($finalChunks.Count -gt 0) { $finalChunks -join '' } else { $fallbackChunks -join '' }
  if ([string]::IsNullOrWhiteSpace($answer)) { throw 'ACP model returned an empty answer' }
  $envelope = [ordered]@{
    text = $answer
    audit = [ordered]@{
      event_types = @($eventTypes | Sort-Object -Unique)
      tool_event_count = [int]$prohibitedEvents.Count
      permission_interaction_count = $interactionCount
      additional_directories = @()
      neutral_cwd = $true
      mode = 'read-only'
    }
  }
  [Console]::Out.Write(($envelope | ConvertTo-Json -Depth 12 -Compress))
} finally {
  if ($runId) {
    # The run may already be completed; cancellation is intentionally not used as a fallback path.
  }
  try { Call-Tool 'acp_session' @{ action='close'; session_id=$sessionId } | Out-Null } catch {}
}
