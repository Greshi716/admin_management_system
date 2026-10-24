# Lists FR ids from the F0 spec that no test references. Report-only unless -Strict is given.
param([switch]$Strict)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$spec = "specs/001-foundation/spec.md"
$frs = Select-String -Path $spec -Pattern '^- \*\*(FR-\d{3})' |
    ForEach-Object { $_.Matches[0].Groups[1].Value } | Sort-Object -Unique

$backend = @()
if (Test-Path management/src/test) {
    $backend = Get-ChildItem management/src/test -Recurse -Filter *.java
}
$frontend = @()
foreach ($dir in "frontend/e2e", "frontend/src") {
    if (Test-Path $dir) {
        $frontend += Get-ChildItem $dir -Recurse -Filter *.spec.ts | Where-Object { $_.FullName -notmatch 'node_modules' }
    }
}

$uncovered = @()
foreach ($fr in $frs) {
    $covered = $false
    if ($backend.Count -gt 0 -and (Select-String -Path $backend.FullName -Pattern "@Covers\(`"$fr`"\)" -Quiet)) { $covered = $true }
    if (-not $covered -and $frontend.Count -gt 0 -and
        (Select-String -Path $frontend.FullName -Pattern "(it|test|describe)(\.[a-z]+)?\s*\(.*$fr" -Quiet)) { $covered = $true }
    if (-not $covered) { $uncovered += $fr }
}

Write-Host "FR coverage: $($frs.Count - $uncovered.Count) of $($frs.Count) covered"
if ($uncovered.Count -gt 0) {
    Write-Host "Uncovered: $($uncovered -join ' ')"
    if ($Strict) { exit 1 }
}
