param([switch]$KeepServices)
. (Join-Path $PSScriptRoot 'local-common.ps1')
Stop-YouxiApplication
if (!$KeepServices) {
    & docker compose --env-file $script:YouxiEnvFile -f $script:YouxiCompose stop
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop dedicated services.' }
}
Write-Host 'Local runtime stopped. Database, Redis and RabbitMQ volumes were retained.'
