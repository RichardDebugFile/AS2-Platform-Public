@echo off
title documents-service :8084
cd /d "%~dp0..\.."
echo ============================================
echo  documents-service  -^>  http://localhost:8084
echo ============================================
call "%~dp0java.bat"
if errorlevel 1 (
  pause
  exit /b 1
)
"%JAVA%" -jar "documents-service\target\documents-service-1.0-SNAPSHOT.jar"
echo.
echo documents-service se detuvo. Presiona una tecla para cerrar.
pause >nul
