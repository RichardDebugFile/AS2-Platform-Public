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
echo [2/3] Servicios (cada uno en su ventana)...
start "" cmd /k call "%~dp0scripts\win\auth.bat"
start "" cmd /k call "%~dp0scripts\win\partners.bat"
start "" cmd /k call "%~dp0scripts\win\documents.bat"
start "" cmd /k call "%~dp0scripts\win\alerts.bat"
start "" cmd /k call "%~dp0scripts\win\as2-adapter.bat"
timeout /t 5 /nobreak >nul
start "" cmd /k call "%~dp0scripts\win\gateway.bat"
echo.
echo [3/3] URLs (salud en /actuator/health):
echo   Gateway      http://localhost:8765
echo   Partners     http://localhost:8081
echo   Alerts       http://localhost:8082
echo   Auth         http://localhost:8083
echo   Documents    http://localhost:8084
echo   AS2 Adapter  http://localhost:8085
echo.
echo Consola web:  scripts\win\frontend.bat   Detener:  stop.bat
endlocal
pause
