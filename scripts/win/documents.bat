@echo off
title documents-service :8084
cd /d "%~dp0..\.."
echo ============================================
echo  documents-service  -^>  http://localhost:8084
echo ============================================
java -jar "documents-service\target\documents-service-1.0-SNAPSHOT.jar"
echo.
echo documents-service se detuvo. Presiona una tecla para cerrar.
pause >nul
