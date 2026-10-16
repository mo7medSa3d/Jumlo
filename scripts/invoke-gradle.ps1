param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleArguments)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'gradle/wrapper/gradle-wrapper.jar'
$expected = '7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172'
if (!(Test-Path -LiteralPath $jar -PathType Leaf)) {
    Write-Error 'WRAPPER_JAR_MISSING: run scripts/restore-wrapper.ps1 -Download explicitly on a network-enabled machine.' -ErrorAction Continue
    exit 2
}
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    Write-Error 'WRAPPER_INTEGRITY_FAILED: refusing to execute this JAR.' -ErrorAction Continue
    exit 2
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
& $java '-Xmx64m' '-Xms64m' '-Dorg.gradle.appname=gradlew' '-classpath' $jar 'org.gradle.wrapper.GradleWrapperMain' @GradleArguments
exit $LASTEXITCODE
