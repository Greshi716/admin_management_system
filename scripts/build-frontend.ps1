# Builds the Angular app and copies it into the backend's static folder, so one jar serves the whole app.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "== frontend: production build =="
Push-Location frontend
npm ci
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
npm run build
if ($LASTEXITCODE -ne 0) { Pop-Location; exit $LASTEXITCODE }
Pop-Location

$target = "management/src/main/resources/static"
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
New-Item -ItemType Directory -Path $target | Out-Null
Copy-Item "frontend/dist/frontend/browser/*" $target -Recurse
Write-Host "Copied the build to $target"
