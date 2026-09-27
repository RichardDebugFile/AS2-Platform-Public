@echo off
setlocal enabledelayedexpansion
echo ================================================================
echo   Plataforma AS2/EDI - Detener servicios
echo ================================================================
for %%P in (8765 8081 8082 8083 8084 8085) do (
  set "FOUND="
  for /f "tokens=5" %%K in ('netstat -ano ^| findstr ":%%P " ^| findstr LISTENING') do (
    echo   Deteniendo PID %%K en el puerto %%P
    taskkill /PID %%K /F >nul 2>&1
    set "FOUND=1"
  )
  if not defined FOUND echo   Puerto %%P libre
)
echo.
echo La base de datos sigue activa. Para apagarla:  docker compose down
endlocal
pause
