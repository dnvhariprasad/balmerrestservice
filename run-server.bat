@echo off
REM Balmer REST Service - Start Script (Windows)
REM Run this script to start the Spring Boot server

echo Starting Balmer REST Service...
echo ================================

REM Change to script directory
cd /d "%~dp0"

REM Check for process on port 8080 and kill it
set "PORT=8080"
echo Checking for process on port %PORT%...

for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%PORT% ^| findstr LISTENING') do (
    set "PID=%%a"
)

if defined PID (
    echo Killing existing process on port %PORT% (PID: %PID%)...
    taskkill /F /PID %PID%
    timeout /t 2 >nul
) else (
    echo Port %PORT% is free.
)

REM Skip tests and run the application
call mvnw.cmd spring-boot:run -DskipTests
pause
