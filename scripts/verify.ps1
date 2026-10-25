# Runs the build and tests. PowerShell version of verify.sh.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$javaCmd = "java"
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { $javaCmd = Join-Path $env:JAVA_HOME "bin\java.exe" }
$javaVersion = (cmd /c "`"$javaCmd`" -version 2>&1" | Select-Object -First 1)
if ($javaVersion -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 21) {
    Write-Host "Java 21 or newer is required, but found: $javaVersion. Set JAVA_HOME to a JDK 21."
    exit 1
}

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

    Write-Host "== frontend: browser tests (start their own backend on port 8080) =="
    if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "Port 8080 is in use. Stop the running backend, then run verify again."
        exit 1
    }
    Push-Location frontend
    npm run e2e
    if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
    Pop-Location
}

Write-Host "== FR coverage (strict: every requirement needs a test) =="
& "$PSScriptRoot/check-fr-coverage.ps1" -Strict
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "VERIFY OK"
