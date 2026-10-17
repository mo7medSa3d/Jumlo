@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\invoke-gradle.ps1" %*
exit /b %ERRORLEVEL%
