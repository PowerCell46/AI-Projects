# Starts the backend with environment loaded from ../.env.
# Usage:  cd backend; .\run-dev.ps1

$envFile = Join-Path $PSScriptRoot '..\.env'

if (-not (Test-Path $envFile)) {
    Write-Error "No .env file found at $envFile - copy .env.example to .env and fill in real values."
    exit 1
}

foreach ($rawLine in Get-Content $envFile) {
    $line = $rawLine.Trim()

    if ($line -eq '' -or $line.StartsWith('#')) {
        continue
    }

    $name, $value = $line -split '=', 2

    if ($value -match '^(your-|replace-with)') {
        Write-Error ".env still contains a placeholder for '$name' - fill in the real value first."
        exit 1
    }

    Set-Item -Path "env:$name" -Value $value
}

$env:JAVA_HOME = "C:\Users\HP ZBook 17 G5\.jdks\openjdk-25"

Set-Location $PSScriptRoot
mvn spring-boot:run
