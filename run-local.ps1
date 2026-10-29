$ErrorActionPreference = "Stop"

$jdk = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot"
if (Test-Path $jdk) {
    $env:JAVA_HOME = $jdk
    $env:Path = "$jdk\bin;$env:Path"
}

$env:SPRING_PROFILES_ACTIVE = "local"
Write-Host "Iniciando DevStreak API com profile local..."
& "$PSScriptRoot\mvnw.cmd" spring-boot:run
