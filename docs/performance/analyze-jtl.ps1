param(
    [Parameter(Mandatory = $true)]
    [string]$Jtl
)

$samples = @(Import-Csv -LiteralPath $Jtl)
if ($samples.Count -eq 0) {
    throw 'JTL 中没有请求样本'
}

$elapsed = @($samples | ForEach-Object { [int]$_.elapsed } | Sort-Object)
$start = ($samples | Measure-Object -Property timeStamp -Minimum).Minimum
$end = ($samples | ForEach-Object { [long]$_.timeStamp + [int]$_.elapsed } | Measure-Object -Maximum).Maximum
$seconds = ([double]$end - [double]$start) / 1000
$failed = @($samples | Where-Object { $_.success -ne 'true' })

function Percentile([double]$fraction) {
    $index = [Math]::Ceiling($elapsed.Count * $fraction) - 1
    return $elapsed[[Math]::Max(0, $index)]
}

[pscustomobject]@{
    Samples = $samples.Count
    Successful = $samples.Count - $failed.Count
    Failed = $failed.Count
    ErrorRatePercent = [Math]::Round(100 * $failed.Count / $samples.Count, 3)
    ActiveSeconds = [Math]::Round($seconds, 2)
    RequestsPerSecond = [Math]::Round($samples.Count / $seconds, 2)
    P50Milliseconds = Percentile 0.50
    P95Milliseconds = Percentile 0.95
    P99Milliseconds = Percentile 0.99
} | Format-List

$samples | Group-Object responseCode | Sort-Object Count -Descending |
    Select-Object Name, Count | Format-Table -AutoSize
