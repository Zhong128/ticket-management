. (Join-Path $PSScriptRoot '../local-common.ps1')

# Mock listeners only inside this test process; never stop real applications.
function Get-NetTCPConnection {
    param($LocalPort, $State, $ErrorAction)
    if ($script:BlockedPorts -contains $LocalPort) { return @{OwningProcess=123} }
}
function Assert-Port($Expected, $Actual) {
    if ($Expected -ne $Actual) { throw "Expected $Expected; got $Actual" }
}
$script:BlockedPorts = @()
Assert-Port 8082 (Resolve-YouxiPort 8082)
$script:BlockedPorts = @(8082)
Assert-Port 8083 (Resolve-YouxiPort 8082)
Assert-Port 8098 (Resolve-YouxiPort 8098 -Explicit)
$rejected = $false
try { Resolve-YouxiPort 8082 -Explicit | Out-Null } catch { $rejected = $true }
if (!$rejected) { throw 'An explicitly occupied port must fail.' }
$script:BlockedPorts = @(8082..8092)
$rejected = $false
try { Resolve-YouxiPort 8082 | Out-Null } catch { $rejected = $true }
if (!$rejected) { throw 'Exhausting the default port range must fail.' }
Write-Host '5 port selection checks passed.'
