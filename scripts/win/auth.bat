@echo off
title auth-service :8083
cd /d "%~dp0..\.."
echo ============================================
echo  auth-service  -^>  http://localhost:8083
echo ============================================
rem Java 25: JAVA_HOME si esta definido; si no, el java del PATH
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -jar "auth-service\target\auth-service-1.0-SNAPSHOT.jar"
echo.
echo auth-service se detuvo. Presiona una tecla para cerrar.
pause >nul
