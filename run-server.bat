@echo off
setlocal EnableExtensions
REM Balmer REST Service - Start Script (Windows)
REM Run this script to start the Spring Boot server

echo Starting Balmer REST Service...
echo ================================

REM Change to script directory
cd /d "%~dp0"

REM Check for process on port 8089 and kill it
set "PORT=8089"
echo Checking for process on port %PORT%...

set "PID="
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /C:":%PORT% " ^| findstr LISTENING') do set "PID=%%a"

if not defined PID goto portfree
echo Killing existing process on port %PORT% (PID: %PID%)...
taskkill /F /PID %PID%
timeout /t 2 >nul
goto portdone

:portfree
echo Port %PORT% is free.
:portdone

REM Skip tests and run the application
call mvnw.cmd spring-boot:run -DskipTests
pause
