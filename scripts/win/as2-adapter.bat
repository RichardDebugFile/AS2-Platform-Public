@echo off
title as2-adapter :8085
cd /d "%~dp0..\.."
echo ============================================
echo  as2-adapter  -^>  http://localhost:8085
echo ============================================
rem Java 25: JAVA_HOME si esta definido; si no, el java del PATH
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -jar "as2-adapter\target\as2-adapter-1.0-SNAPSHOT.jar"
echo.
echo as2-adapter se detuvo. Presiona una tecla para cerrar.
pause >nul
