# Runs the build and tests. PowerShell version of verify.sh.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "== backend: build and test =="
Push-Location management
.\mvnw.cmd -B verify
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
Pop-Location

if (Test-Path frontend/package.json) {
    Write-Host "== frontend: build =="
    Push-Location frontend
    npm run build
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    Pop-Location
}

Write-Host "VERIFY OK"
