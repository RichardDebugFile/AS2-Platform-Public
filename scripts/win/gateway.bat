@echo off
title gateway :8765
cd /d "%~dp0..\.."
echo ============================================
echo  gateway  -^>  http://localhost:8765
echo ============================================
rem Java 25: JAVA_HOME si esta definido; si no, el java del PATH
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -jar "gateway\target\gateway-1.0-SNAPSHOT.jar"
echo.
echo gateway se detuvo. Presiona una tecla para cerrar.
pause >nul
