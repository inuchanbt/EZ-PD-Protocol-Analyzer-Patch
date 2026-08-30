@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Patch-Analyzer.ps1" %*
echo.
pause
