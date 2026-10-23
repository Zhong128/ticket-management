param([string]$Revision = '726217ae65ecb18a1b2902c2b08cac43878caa1f')

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$targetRoot = Join-Path $repoRoot 'ticket-management/target'
$destination = Join-Path $targetRoot ('baseline-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$archive = Join-Path $targetRoot 'performance-baseline.zip'
New-Item -ItemType Directory -Force $destination | Out-Null
& git -C $repoRoot archive --format=zip "--output=$archive" $Revision
if ($LASTEXITCODE -ne 0) { throw '无法导出基线提交' }
Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force

# 只适配验收驱动；导出的业务源码、POM和V1～V4不修改。
$testFile = Join-Path $destination 'ticket-management/src/test/java/org/example/ticketmanagement/trade/messaging/TradeLoadIT.java'
$source = [IO.File]::ReadAllText($testFile).Replace("`r`n", "`n")
$oldMethod = '    void sustainedReservationsUseFreshUsersAndStock() throws Exception {'
if (-not $source.Contains($oldMethod)) { throw '该提交的驱动结构与适配脚本不符' }
$newMethod = @'
    void sustainedReservationsUseFreshUsersAndStock() throws Exception {
        for (int round = 1; round <= 3; round++) {
            runBaseline(300, round);
        }
    }

    private int tierId = 4000;
    private long nextBuyer = 100000;

    private void runBaseline(int duration, int round) throws Exception {
        tierId++;
'@
$source = $source.Replace($oldMethod, $newMethod)
$source = $source.Replace('        int duration = setting("LOAD_DURATION_SECONDS", 300);' + "`n", '')
$source = $source.Replace('seedSale(buyers + 1000);', 'seedSale(buyers * 3 + 1000);')
$source = $source.Replace('"-Jhost=127.0.0.1", "-Jport=" + port,', '"-Jhost=127.0.0.1", "-Jport=" + port, "-Jport2=" + port, "-Jmode=abundant",')
$source = $source.Replace('LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))', 'LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-round" + round)')
$source = $source.Replace('writer.write("token,tierId\n");', 'writer.write("token,tierId,requestKey,userIndex,quantity\n");')
$source = $source.Replace('long userId = 100000L + i;', 'long userId = nextBuyer++;')
$source = $source.Replace('writer.write(",4001\n");', 'writer.write("," + tierId + ",load-" + userId + "," + i + ",1\n");')
$source = $source.Replace('"SELECT COUNT(*) FROM stock_reservation WHERE tier_id=4001", Integer.class', '"SELECT COUNT(*) FROM stock_reservation WHERE tier_id=?", Integer.class, tierId')
$source = $source.Replace('"SELECT COUNT(*) FROM stock_reservation", Integer.class', '"SELECT COUNT(*) FROM stock_reservation WHERE tier_id=?", Integer.class, tierId')
$source = $source.Replace('"SELECT COUNT(*) FROM order_info WHERE tier_id=4001", Integer.class', '"SELECT COUNT(*) FROM order_info WHERE tier_id=?", Integer.class, tierId')
$source = $source.Replace('VALUES(4001,''Load city'',''LOAD'',''Test province'')");', 'VALUES(?,''Load city'',?,''Test province'')", tierId, "L" + tierId);')
$source = $source.Replace('VALUES(4001,''Load category'')");', 'VALUES(?,''Load category'')", tierId);')
$source = $source.Replace('VALUES(4001,''Load event'',4001,4001,''Load venue'',1,?,?,?,?)",' + "`n" + '                now', 'VALUES(?,''Load event'',?, ?,''Load venue'',1,?,?,?,?)",' + "`n" + '                tierId, tierId, tierId, now')
$source = $source.Replace('VALUES(4001,4001,''Load session'',?,1)", now.plusDays(2)', 'VALUES(?, ?,''Load session'',?,1)", tierId, tierId, now.plusDays(2)')
$source = $source.Replace('VALUES(4001,4001,4001,''Load tier'',100,100,?,?,1)",' + "`n" + '                stock, stock', 'VALUES(?, ?, ?,''Load tier'',100,100,?,?,1)",' + "`n" + '                tierId, tierId, tierId, stock, stock')
$oldProcess = 'Process process = new ProcessBuilder(command).redirectErrorStream(true)' + "`n" + '                .redirectOutput(output.toFile()).start();'
$newProcess = @'
ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("JVM_ARGS", "-Djdk.net.unixdomain.tmpdir=" + System.getProperty("jdk.net.unixdomain.tmpdir"));
        Process process = builder.start();
'@
$source = $source.Replace($oldProcess, $newProcess)
[IO.File]::WriteAllText($testFile, $source, [Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'reservation.jmx') -Destination (Join-Path $destination 'docs/performance/reservation.jmx') -Force
Write-Output $destination
