@echo off
title partners-service :8081
cd /d "%~dp0..\.."
echo ============================================
echo  partners-service  -^>  http://localhost:8081
echo ============================================
rem Java 25: JAVA_HOME si esta definido; si no, el java del PATH
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -jar "partners-service\target\partners-service-1.0-SNAPSHOT.jar"
echo.
echo partners-service se detuvo. Presiona una tecla para cerrar.
pause >nul
