@echo off
title partners-service :8081
cd /d "%~dp0..\.."
echo ============================================
echo  partners-service  -^>  http://localhost:8081
echo ============================================
call "%~dp0java.bat"
if errorlevel 1 (
  pause
  exit /b 1
)
"%JAVA%" -jar "partners-service\target\partners-service-1.0-SNAPSHOT.jar"
echo.
echo partners-service se detuvo. Presiona una tecla para cerrar.
pause >nul
