@echo off
setlocal
cd /d "%~dp0"
echo ================================================================
echo   Plataforma AS2/EDI - Iniciar base de datos y servicios
echo ================================================================
echo.
echo [1/3] Base de datos (docker compose)...
docker compose up -d db
if errorlevel 1 (
  echo   AVISO: docker compose fallo. Esta Docker Desktop encendido?
)
echo.
if not exist "gateway\target\gateway-1.0-SNAPSHOT.jar" (
  echo ERROR: no se encontraron los JAR. Ejecuta primero build.bat
  pause
  exit /b 1
)
call "%~dp0scripts\win\java.bat"
if errorlevel 1 (
  pause
  exit /b 1
)
echo Java: %JAVA%
rem Cada ventana hereda AS2_JAVA y usa el mismo Java sin volver a buscar.
set "AS2_JAVA=%JAVA%"
"%JAVA%" -version
echo.
echo [2/3] Servicios (cada uno en su ventana)...
start "" cmd /k call "%~dp0scripts\win\auth.bat"
start "" cmd /k call "%~dp0scripts\win\partners.bat"
start "" cmd /k call "%~dp0scripts\win\documents.bat"
start "" cmd /k call "%~dp0scripts\win\alerts.bat"
start "" cmd /k call "%~dp0scripts\win\as2-adapter.bat"
timeout /t 5 /nobreak >nul
start "" cmd /k call "%~dp0scripts\win\gateway.bat"
rem La consola se abre cuando el gateway responde (hasta 2 min): antes, el inicio de sesion fallaria.
echo   Esperando a que el gateway responda...
set /a AS2_ESPERA=0
:esperar_gateway
curl -s -f -o nul http://localhost:8765/actuator/health >nul 2>&1
if not errorlevel 1 goto :gateway_listo
set /a AS2_ESPERA+=1
if %AS2_ESPERA% GEQ 60 goto :gateway_listo
timeout /t 2 /nobreak >nul
goto :esperar_gateway
:gateway_listo
where npm >nul 2>&1
if errorlevel 1 (
  echo   AVISO: no se encontro npm ^(Node 22^): la consola web no se abre.
) else (
  start "" cmd /k call "%~dp0scripts\win\frontend.bat"
)
echo.
echo [3/3] URLs (salud en /actuator/health):
echo   Gateway      http://localhost:8765
echo   Partners     http://localhost:8081
echo   Alerts       http://localhost:8082
echo   Auth         http://localhost:8083
echo   Documents    http://localhost:8084
echo   AS2 Adapter  http://localhost:8085
echo   Consola web  http://localhost:5173  ^(se abre sola en el navegador^)
echo.
echo Detener todo:  stop.bat
endlocal
pause
