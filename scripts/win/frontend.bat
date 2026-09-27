@echo off
title consola web - Vite
cd /d "%~dp0..\..\frontend"
if not exist node_modules (
  echo Instalando dependencias...
  call npm ci
)
call npm run dev
pause >nul
