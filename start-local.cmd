@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-local.ps1" -OpenBrowser %*
set "RUN_EXIT=%errorlevel%"
if not "%RUN_EXIT%"=="0" pause
exit /b %RUN_EXIT%
