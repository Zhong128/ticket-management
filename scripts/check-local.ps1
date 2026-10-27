param([ValidateRange(1024,65535)][int]$AppPort=8082)
. (Join-Path $PSScriptRoot 'local-common.ps1')
if (!$PSBoundParameters.ContainsKey('AppPort')) {
    $stateFile = Join-Path $script:YouxiOutput 'application.json'
    if (Test-Path -LiteralPath $stateFile) {
        $AppPort = [int](Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json).port
    }
}
$settings = Get-YouxiSettings
$status = Invoke-RestMethod "http://127.0.0.1:$AppPort/api/public/app-status" -TimeoutSec 5
if ($status.code -ne 1 -or !$status.data.ready -or !$status.data.tradingEnabled -or $status.data.instanceId -ne $settings.YOUXI_INSTANCE_ID) {
    throw 'Expected local trading runtime is not ready.'
}
$homeResponse = Invoke-WebRequest "http://127.0.0.1:$AppPort/" -UseBasicParsing -TimeoutSec 5
$route = Invoke-WebRequest "http://127.0.0.1:$AppPort/events" -UseBasicParsing -TimeoutSec 5
if ($homeResponse.Content -notmatch 'id="app"' -or $route.Content -notmatch 'id="app"') { throw 'Bundled frontend or direct navigation failed.' }
Write-Host "Ready: http://127.0.0.1:$AppPort"
Write-Host "Trading enabled: $($status.data.tradingEnabled)"
Write-Host "Customer service enabled: $($status.data.customerServiceEnabled)"
Write-Host 'Bundled frontend and direct navigation: OK. No mail, AI or payment was invoked.'
