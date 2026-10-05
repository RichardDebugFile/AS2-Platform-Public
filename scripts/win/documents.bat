@echo off
title documents-service :8084
cd /d "%~dp0..\.."
echo ============================================
echo  documents-service  -^>  http://localhost:8084
echo ============================================
rem Java 25: JAVA_HOME si esta definido; si no, el java del PATH
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -jar "documents-service\target\documents-service-1.0-SNAPSHOT.jar"
echo.
echo documents-service se detuvo. Presiona una tecla para cerrar.
pause >nul
