@echo off
title as2-adapter :8085
cd /d "%~dp0..\.."
echo ============================================
echo  as2-adapter  -^>  http://localhost:8085
echo ============================================
java -jar "as2-adapter\target\as2-adapter-1.0-SNAPSHOT.jar"
echo.
echo as2-adapter se detuvo. Presiona una tecla para cerrar.
pause >nul
