@echo off
title alerts-service :8082
cd /d "%~dp0..\.."
echo ============================================
echo  alerts-service  -^>  http://localhost:8082
echo ============================================
rem Java 25: JAVA_HOME si esta definido; si no, el java del PATH
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -jar "alerts-service\target\alerts-service-1.0-SNAPSHOT.jar"
echo.
echo alerts-service se detuvo. Presiona una tecla para cerrar.
pause >nul
