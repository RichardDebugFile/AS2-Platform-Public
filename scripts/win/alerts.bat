@echo off
title alerts-service :8082
cd /d "%~dp0..\.."
echo ============================================
echo  alerts-service  -^>  http://localhost:8082
echo ============================================
call "%~dp0java.bat"
if errorlevel 1 (
  pause
  exit /b 1
)
"%JAVA%" -jar "alerts-service\target\alerts-service-1.0-SNAPSHOT.jar"
echo.
echo alerts-service se detuvo. Presiona una tecla para cerrar.
pause >nul
