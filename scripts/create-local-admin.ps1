param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z][A-Za-z0-9_]{2,19}$')][string]$Username)
. (Join-Path $PSScriptRoot 'local-common.ps1')
$settings = Get-YouxiSettings
$java = Get-YouxiJava
New-Item -ItemType Directory -Path $script:YouxiOutput -Force | Out-Null
$runtimeClasspath = Join-Path $script:YouxiModule 'target/runtime-classpath.txt'
if (!(Test-Path -LiteralPath $runtimeClasspath)) { throw 'Build the local runtime first using start-local.cmd.' }
Use-YouxiEnvironment $settings 8082 {
    # 工具还会独立校验目标，只能新建本地账号，不能覆盖或提权已有账号。
    $classpath = 'target/classes;' + (Get-Content -LiteralPath $runtimeClasspath -Raw).Trim()
    Invoke-YouxiCommand $script:YouxiModule $java @('-cp',$classpath,'org.example.ticketmanagement.maintenance.LocalAdminTool','create',$Username) (Join-Path $script:YouxiOutput 'admin-initialization.log')
}
Write-Host "Local administrator created. Credentials: $script:YouxiRoot/output/local-admin-$Username.txt"
Write-Host 'Do not commit or share this file. Existing users were not changed.'
