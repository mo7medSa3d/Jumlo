param([switch]$Download, [string]$From)
$ErrorActionPreference = 'Stop'
if (($Download -and $From) -or (!$Download -and !$From)) { throw 'Choose -Download or -From PATH explicitly.' }
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root 'gradle/wrapper/gradle-wrapper.jar'
$temp = "$dest.$([Guid]::NewGuid()).tmp"
$expected = '7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172'
try {
    if ($From) { Copy-Item -LiteralPath $From -Destination $temp }
    else {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.14.0/gradle/wrapper/gradle-wrapper.jar' -OutFile $temp -TimeoutSec 90
    }
    if ((Get-FileHash -LiteralPath $temp -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'WRAPPER_INTEGRITY_FAILED: bytes rejected.' }
    Move-Item -LiteralPath $temp -Destination $dest -Force
    Write-Output 'Restored official Gradle 8.14 wrapper; SHA256 verified. No SDK/distribution installed.'
} finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
