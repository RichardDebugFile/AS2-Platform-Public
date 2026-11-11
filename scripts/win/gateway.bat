@echo off
title gateway :8765
cd /d "%~dp0..\.."
echo ============================================
echo  gateway  -^>  http://localhost:8765
echo ============================================
call "%~dp0java.bat"
if errorlevel 1 (
  pause
  exit /b 1
)
"%JAVA%" -jar "gateway\target\gateway-1.0-SNAPSHOT.jar"
echo.
echo gateway se detuvo. Presiona una tecla para cerrar.
pause >nul
