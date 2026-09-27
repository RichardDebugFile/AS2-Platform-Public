@echo off
title partners-service :8081
cd /d "%~dp0..\.."
echo ============================================
echo  partners-service  -^>  http://localhost:8081
echo ============================================
java -jar "partners-service\target\partners-service-1.0-SNAPSHOT.jar"
echo.
echo partners-service se detuvo. Presiona una tecla para cerrar.
pause >nul
