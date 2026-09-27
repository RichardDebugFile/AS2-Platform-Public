@echo off
setlocal
cd /d "%~dp0"
echo ================================================================
echo   Plataforma AS2/EDI - Compilar todos los modulos
echo ================================================================
call mvn -B clean package -DskipTests
if errorlevel 1 (
  echo.
  echo ERROR: la compilacion fallo. Revisa la salida de Maven.
  pause
  exit /b 1
)
echo.
echo Compilacion OK. Ahora puedes ejecutar start.bat
endlocal
pause
