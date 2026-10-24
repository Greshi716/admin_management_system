# Runs the build and tests. PowerShell version of verify.sh.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "== backend: build and test =="
Push-Location management
.\mvnw.cmd -B verify
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
Pop-Location

if (Test-Path frontend/package.json) {
    Write-Host "== frontend: install, build and test =="
    Push-Location frontend
    npm ci
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    npm run build
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    npm test -- --watch=false
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    Pop-Location
}

Write-Host "== FR coverage (report only; strict mode is switched on in T099) =="
& "$PSScriptRoot/check-fr-coverage.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "VERIFY OK"
