@echo off
title auth-service :8083
cd /d "%~dp0..\.."
echo ============================================
echo  auth-service  -^>  http://localhost:8083
echo ============================================
java -jar "auth-service\target\auth-service-1.0-SNAPSHOT.jar"
echo.
echo auth-service se detuvo. Presiona una tecla para cerrar.
pause >nul
