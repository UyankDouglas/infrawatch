<#
 InfraWatch — controle determinístico dos serviços de demonstração (PowerShell).
 Uso: .\scripts\demo.ps1 status | fail [porta] | recover [porta] | slow <ms> [porta] | pattern "ok,ok,fail" [porta] | reset
#>
param(
  [Parameter(Position = 0)] [string] $Command = "status",
  [Parameter(Position = 1)] [string] $Arg1,
  [Parameter(Position = 2)] [string] $Arg2
)
$HostName = if ($env:DEMO_HOST) { $env:DEMO_HOST } else { "localhost" }
function Ctl($port, $action, $body = "{}") {
  Invoke-RestMethod -Method Post -Uri "http://$HostName`:$port/__control/$action" -ContentType "application/json" -Body $body | ConvertTo-Json -Compress
}
switch ($Command) {
  "status"  { foreach ($p in 9101, 9102, 9103) { "$p`: " + (Invoke-RestMethod "http://$HostName`:$p/__control" | ConvertTo-Json -Compress) } }
  "fail"    { $p = if ($Arg1) { $Arg1 } else { 9103 }; Ctl $p "down" '{"status":503}' }
  "recover" { $p = if ($Arg1) { $Arg1 } else { 9103 }; Ctl $p "up" }
  "slow"    { if (-not $Arg1) { throw "informe os ms" }; $p = if ($Arg2) { $Arg2 } else { 9102 }; Ctl $p "latency" "{`"ms`":$Arg1}" }
  "pattern" { if (-not $Arg1) { throw "informe o padrão" }; $p = if ($Arg2) { $Arg2 } else { 9103 }; Ctl $p "pattern" "{`"pattern`":`"$Arg1`"}" }
  "reset"   { foreach ($p in 9101, 9102, 9103) { Ctl $p "reset" } }
  default   { Get-Content $PSCommandPath -TotalCount 4 }
}
