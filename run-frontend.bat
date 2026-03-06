@echo off
setlocal EnableExtensions

REM Balmer REST Service - Frontend Startup Script (Windows)

set "SCRIPT_DIR=%~dp0"
set "FRONTEND_DIR=%SCRIPT_DIR%frontend"

echo ==========================================
echo   Balmer REST Service - Frontend
echo ==========================================

if not exist "%FRONTEND_DIR%\" (
  echo Error: Frontend directory not found at %FRONTEND_DIR%
  exit /b 1
)

pushd "%FRONTEND_DIR%"

if not exist "node_modules\" (
  echo Installing dependencies...
  npm install
  if errorlevel 1 (
    echo Error: npm install failed
    popd
    exit /b 1
  )
  echo.
)

echo Starting frontend development server...
echo URL: http://localhost:3000
echo.
echo Note: Make sure the backend is running on http://localhost:8089
echo       (Configure backend URL in frontend/.env file)
echo Press Ctrl+C to stop the server
echo ==========================================
echo.

npm run dev

popd
