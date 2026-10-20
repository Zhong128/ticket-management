param([Parameter(Mandatory=$true)][ValidateSet('baseline-existing','migrate','validate')][string]$Operation)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    foreach ($name in @('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { throw "Missing $name" }
    }
    if (!(Test-Path -LiteralPath 'target/classes/org/example/ticketmanagement/trade/TradeMigrationTool.class')) {
        throw 'Build first: ./mvnw.cmd -DskipTests package'
    }
    $maven = if (Test-Path -LiteralPath './mvnw.cmd') { './mvnw.cmd' } else { 'mvn.cmd' }
    & $maven -q dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/runtime-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve migration runtime classpath' }
    $dependencies = (Get-Content -LiteralPath 'target/runtime-classpath.txt' -Raw).Trim()
    & java -cp "target/classes;$dependencies" org.example.ticketmanagement.trade.TradeMigrationTool $Operation
    if ($LASTEXITCODE -ne 0) { throw "Migration operation failed: $Operation" }
} finally { Pop-Location }
