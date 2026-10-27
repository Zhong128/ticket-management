$ErrorActionPreference = 'Stop'
$script:YouxiRoot = Split-Path $PSScriptRoot -Parent
$script:YouxiModule = Join-Path $script:YouxiRoot 'ticket-management'
$script:YouxiOutput = Join-Path $script:YouxiRoot 'output/youxi-local'
$script:YouxiEnvFile = Join-Path $script:YouxiModule '.env.youxi-local'
$script:YouxiCompose = Join-Path $script:YouxiModule 'compose.local.yml'

function Get-YouxiSettings([switch]$Initialize) {
    if (!(Test-Path -LiteralPath $script:YouxiEnvFile)) {
        if (!$Initialize) { throw 'Run scripts/start-local.ps1 first.' }
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $values = @()
            foreach ($key in @('YOUXI_LOCAL_DB_PASSWORD','YOUXI_LOCAL_MQ_PASSWORD','YOUXI_INSTANCE_ID')) {
                $bytes = New-Object byte[] 24
                $random.GetBytes($bytes)
                $value = [BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
                $values += "$key=$value"
            }
            [IO.File]::WriteAllLines($script:YouxiEnvFile, $values, (New-Object Text.UTF8Encoding($false)))
        } finally { $random.Dispose() }
    }
    $settings = @{}
    foreach ($line in [IO.File]::ReadAllLines($script:YouxiEnvFile)) {
        if ($line -match '^([A-Z_]+)=(.+)$') { $settings[$Matches[1]] = $Matches[2] }
    }
    foreach ($key in @('YOUXI_LOCAL_DB_PASSWORD','YOUXI_LOCAL_MQ_PASSWORD','YOUXI_INSTANCE_ID')) {
        if (!$settings[$key] -or $settings[$key] -notmatch '^[a-f0-9]{48}$') { throw "Invalid local setting: $key" }
    }
    return $settings
}

function Get-YouxiJava {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin/java.exe') }
    $candidates += 'C:/Program Files/Java/jdk-21/bin/java.exe'
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($command) { $candidates += $command.Source }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            $version = (& $candidate --version 2>&1 | Out-String)
            if ($version -match '(?m)^(?:java|openjdk) 21[.\s]') { return $candidate }
        }
    }
    throw 'Java 21 is required; set JAVA_HOME.'
}

function Get-YouxiMaven {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $wrapper = Join-Path $script:YouxiModule 'mvnw.cmd'
    if (Test-Path -LiteralPath $wrapper) { return $wrapper }
    $cache = Join-Path $env:USERPROFILE '.m2/wrapper/dists'
    $cached = Get-ChildItem -LiteralPath $cache -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($cached) { return $cached.FullName }
    throw 'Maven is required; add mvn.cmd to PATH.'
}

function Invoke-YouxiCommand([string]$Directory, [string]$Program, [string[]]$Arguments, [string]$LogFile) {
    Push-Location $Directory
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell treats native stderr progress as errors; use the exit code.
        $ErrorActionPreference = 'Continue'
        & $Program @Arguments *> $LogFile
        $ErrorActionPreference = $previousPreference
        if ($LASTEXITCODE -ne 0) { throw "Command failed; see $LogFile" }
    } finally { $ErrorActionPreference = $previousPreference; Pop-Location }
}

function Use-YouxiEnvironment([hashtable]$Settings, [int]$AppPort, [scriptblock]$Action) {
    $values = @{
        SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3308/youxi_local?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
        SPRING_DATASOURCE_USERNAME='root'; SPRING_DATASOURCE_PASSWORD=$Settings.YOUXI_LOCAL_DB_PASSWORD
        SPRING_DATA_REDIS_HOST='127.0.0.1'; SPRING_DATA_REDIS_PORT='6381'; SPRING_DATA_REDIS_DATABASE='0'; SPRING_DATA_REDIS_PASSWORD=''
        SPRING_RABBITMQ_HOST='127.0.0.1'; SPRING_RABBITMQ_PORT='5673'; SPRING_RABBITMQ_USERNAME='youxi'; SPRING_RABBITMQ_PASSWORD=$Settings.YOUXI_LOCAL_MQ_PASSWORD; SPRING_RABBITMQ_VIRTUAL_HOST='/youxi'
        TRADE_ENABLED='true'; TRADE_MIGRATIONS_ENABLED='false'
        ALIPAY_GATEWAY_URL='https://openapi-sandbox.dl.alipaydev.com/gateway.do'
        ALIPAY_RETURN_URL="http://127.0.0.1:$AppPort/payment/return"
        ALIPAY_NOTIFY_URL="http://127.0.0.1:$AppPort/api/public/payments/notify"
        REDISSON_CONFIG="singleServerConfig:`n  address: `"redis://127.0.0.1:6381`"`n  database: 0`n  connectionMinimumIdleSize: 2`n  connectionPoolSize: 16`n  subscriptionConnectionMinimumIdleSize: 1`n  subscriptionConnectionPoolSize: 2`nthreads: 2`nnettyThreads: 2`n"
    }
    $saved = @{}
    foreach ($key in $values.Keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process'); [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process') }
    try { & $Action } finally { foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') } }
}

function Resolve-YouxiPort([int]$PreferredPort, [switch]$Explicit) {
    $lastPort = $PreferredPort
    if (!$Explicit) { $lastPort = [Math]::Min($PreferredPort + 10, 65535) }
    for ($candidatePort = $PreferredPort; $candidatePort -le $lastPort; $candidatePort++) {
        $listener = Get-NetTCPConnection -LocalPort $candidatePort -State Listen -ErrorAction SilentlyContinue
        if (!$listener) {
            if ($candidatePort -ne $PreferredPort) { Write-Host "Port $PreferredPort is occupied; using $candidatePort. No other application was stopped." }
            return $candidatePort
        }
    }
    throw "No free application port in $PreferredPort..$lastPort. Choose -AppPort; no other application was stopped."
}

function Stop-YouxiApplication {
    $state = Join-Path $script:YouxiOutput 'application.json'
    if (!(Test-Path -LiteralPath $state)) { return }
    $info = Get-Content -LiteralPath $state -Raw | ConvertFrom-Json
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($info.pid)" -ErrorAction SilentlyContinue
    if ($process) {
        $expectedJar = Join-Path $script:YouxiOutput 'ticket-management.jar'
        if ($process.CommandLine -notlike '*-Dyouxi.local.instance=youxi-local*' -or !$process.CommandLine.Contains($expectedJar)) {
            throw 'Recorded PID no longer belongs to this runtime; refusing to stop it.'
        }
        Stop-Process -Id $process.ProcessId
        # Wait for the owned process to release its listener before allowing restart.
        $stopDeadline = [DateTime]::UtcNow.AddSeconds(20)
        do {
            $remaining = Get-Process -Id $process.ProcessId -ErrorAction SilentlyContinue
            if (!$remaining -or $remaining.HasExited) { return }
            Start-Sleep -Milliseconds 200
        } while ([DateTime]::UtcNow -lt $stopDeadline)
        throw 'Local application has not stopped; refusing to continue.'
    }
}
