@echo off
setlocal
cd /d "%~dp0"
echo ================================================================
echo   Plataforma AS2/EDI - Compilar todos los modulos
echo ================================================================
rem Maven corre con el JDK de JAVA_HOME: se apunta al Java 25 que encuentre java.bat.
rem Si no lo encuentra, Maven sigue con su configuracion (toolchains) y start.bat avisara.
call "%~dp0scripts\win\java.bat"
if defined JAVA for %%F in ("%JAVA%") do set "JAVA_HOME=%%~dpF.."
if defined JAVA echo Java: %JAVA%
echo.
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
