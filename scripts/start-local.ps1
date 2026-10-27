param([switch]$NoBuild, [switch]$Restart, [switch]$OpenBrowser, [switch]$EnableCustomerService, [ValidateRange(1024,65535)][int]$AppPort=8082)
. (Join-Path $PSScriptRoot 'local-common.ps1')
$settings = Get-YouxiSettings -Initialize
New-Item -ItemType Directory -Path $script:YouxiOutput -Force | Out-Null
$stateFile = Join-Path $script:YouxiOutput 'application.json'
if (Test-Path -LiteralPath $stateFile) {
    $previous = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $existing = Get-Process -Id $previous.pid -ErrorAction SilentlyContinue
    if ($existing -and !$Restart) {
        $status = Invoke-RestMethod "http://127.0.0.1:$($previous.port)/api/public/app-status" -TimeoutSec 4
        if ($status.data.instanceId -eq $settings.YOUXI_INSTANCE_ID -and $status.data.ready) {
            Write-Host "Youxi is already running: http://127.0.0.1:$($previous.port)"
            if ($OpenBrowser) { Start-Process "http://127.0.0.1:$($previous.port)" }
            return
        }
        throw 'Recorded process is not a ready local runtime; inspect it or restart explicitly.'
    }
    if ($existing) { Stop-YouxiApplication }
}
$AppPort = Resolve-YouxiPort $AppPort -Explicit:($PSBoundParameters.ContainsKey('AppPort'))
$java = Get-YouxiJava
$env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
if (!(Test-Path -LiteralPath (Join-Path $script:YouxiModule 'config/application-local.properties'))) {
    throw 'Configure ticket-management/config/application-local.properties using the existing template first.'
}
Write-Host 'Starting dedicated MySQL 3308, Redis 6381 and RabbitMQ 5673; existing volumes are untouched.'
Invoke-YouxiCommand $script:YouxiModule 'docker' @('compose','--env-file',$script:YouxiEnvFile,'-f',$script:YouxiCompose,'up','-d','--wait','--wait-timeout','150') (Join-Path $script:YouxiOutput 'infrastructure.log')
$jar = Join-Path $script:YouxiModule 'target/ticket-management-0.0.1-SNAPSHOT.jar'
if (!$NoBuild -or !(Test-Path -LiteralPath $jar)) {
    $frontend = Join-Path $script:YouxiRoot 'frontend'
    if (!(Test-Path -LiteralPath (Join-Path $frontend 'node_modules'))) {
        Invoke-YouxiCommand $frontend 'npm.cmd' @('ci') (Join-Path $script:YouxiOutput 'npm-install.log')
    }
    Write-Host 'Building Vue and the executable Spring Boot JAR.'
    Invoke-YouxiCommand $frontend 'npm.cmd' @('run','build') (Join-Path $script:YouxiOutput 'frontend-build.log')
    $maven = Get-YouxiMaven
    Invoke-YouxiCommand $script:YouxiModule $maven @('-q','-DskipTests','package','dependency:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile=target/runtime-classpath.txt') (Join-Path $script:YouxiOutput 'backend-build.log')
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    if (!$archive.GetEntry('BOOT-INF/classes/static/index.html')) { throw 'Frontend is absent from JAR; rerun without -NoBuild.' }
} finally { $archive.Dispose() }
$runtimeJar = Join-Path $script:YouxiOutput 'ticket-management.jar'
Copy-Item -LiteralPath $jar -Destination $runtimeJar -Force
$runtimeConfig = Join-Path $script:YouxiOutput 'runtime.properties'
$configText = @"
server.port=$AppPort
server.address=127.0.0.1
spring.flyway.enabled=false
spring.data.redis.password=
trade.enabled=true
spring.rabbitmq.dynamic=true
spring.rabbitmq.listener.simple.auto-startup=true
spring.rabbitmq.listener.direct.auto-startup=true
mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl
logging.level.org.springframework.ai.chat.client.advisor=INFO
app.instance-id=$($settings.YOUXI_INSTANCE_ID)
customer-service.enabled=$($EnableCustomerService.IsPresent.ToString().ToLowerInvariant())
spring.mail.properties.mail.smtp.connectiontimeout=10000
spring.mail.properties.mail.smtp.timeout=10000
spring.mail.properties.mail.smtp.writetimeout=10000
"@
[IO.File]::WriteAllText($runtimeConfig, $configText, (New-Object Text.UTF8Encoding($false)))
Use-YouxiEnvironment $settings $AppPort {
    # Only this script-owned, loopback database is migrated. Normal app startup never migrates.
    if ($env:SPRING_DATASOURCE_URL -notlike 'jdbc:mysql://127.0.0.1:3308/youxi_local?*') { throw 'Unexpected migration target.' }
    $classpath = 'target/classes;' + (Get-Content -LiteralPath (Join-Path $script:YouxiModule 'target/runtime-classpath.txt') -Raw).Trim()
    Write-Host 'Explicitly migrating only youxi_local to the latest schema.'
    Invoke-YouxiCommand $script:YouxiModule $java @('-cp',$classpath,'org.example.ticketmanagement.trade.TradeMigrationTool','migrate') (Join-Path $script:YouxiOutput 'migration.log')
    Invoke-YouxiCommand $script:YouxiModule $java @('-cp',$classpath,'org.example.ticketmanagement.trade.TradeMigrationTool','validate') (Join-Path $script:YouxiOutput 'migration-validation.log')
    $manifest = Join-Path $script:YouxiOutput ('catalog-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.json')
    Invoke-YouxiCommand $script:YouxiModule $java @('-cp',$classpath,'org.example.ticketmanagement.maintenance.DemoCatalogTool','import','src/main/resources/demo/catalog-2026-10.json',$manifest) (Join-Path $script:YouxiOutput 'catalog-import.log')
    $configurationUri = ([Uri]$runtimeConfig).AbsoluteUri
    $temporaryDirectory = Join-Path $script:YouxiModule 'target'
    $arguments = @('-Dyouxi.local.instance=youxi-local',"-Djdk.net.unixdomain.tmpdir=$temporaryDirectory",'-Dfile.encoding=UTF-8','-jar',$runtimeJar,"--spring.config.additional-location=$configurationUri")
    $quoted = $arguments | ForEach-Object { '"' + $_.Replace('"','\"') + '"' }
    $application = Start-Process -FilePath $java -ArgumentList $quoted -WorkingDirectory $script:YouxiModule -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $script:YouxiOutput 'application.log') -RedirectStandardError (Join-Path $script:YouxiOutput 'application-error.log')
    @{pid=$application.Id;port=$AppPort;instanceId=$settings.YOUXI_INSTANCE_ID} | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
}
Write-Host 'Waiting for the migrated application and consumers to become ready.'
$ready = $false
for ($attempt=0; $attempt -lt 90; $attempt++) {
    try {
        $status = Invoke-RestMethod "http://127.0.0.1:$AppPort/api/public/app-status" -TimeoutSec 3
        if ($status.code -eq 1 -and $status.data.ready -and $status.data.tradingEnabled -and $status.data.instanceId -eq $settings.YOUXI_INSTANCE_ID) { $ready=$true; break }
    } catch { }
    Start-Sleep -Seconds 1
}
if (!$ready) { throw "Application did not become ready; see $script:YouxiOutput/application.log" }
Write-Host "Ready: http://127.0.0.1:$AppPort"
Write-Host 'Trading is enabled in the dedicated local database. Payments use Alipay sandbox only.'
Write-Host 'Local callback confirmation uses server queries; configure and verify a public tunnel separately for asynchronous callbacks.'
if ($OpenBrowser) { Start-Process "http://127.0.0.1:$AppPort" }
