@echo off
setlocal EnableExtensions

REM Build WAR for JBoss EAP 7 using JDK 11
set "JAVA_HOME=C:\Program Files\Java\jdk-11"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using JAVA_HOME=%JAVA_HOME%
java -version

REM Clean and package the WAR (skip tests for faster build)
call mvnw.cmd -DskipTests clean package

if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo Build complete. WAR output is under target\
endlocal
