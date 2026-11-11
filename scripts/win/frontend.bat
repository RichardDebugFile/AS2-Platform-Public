@echo off
title consola web - Vite
cd /d "%~dp0..\..\frontend"
if not exist node_modules (
  echo Instalando dependencias...
  call npm ci
)
rem --open abre el navegador cuando Vite esta listo.
call npm run dev -- --open
pause >nul
