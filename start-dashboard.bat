@echo off
setlocal
set BACKEND_PORT=8090

cd /d "%~dp0backend"

if not exist "..\frontend\dist\index.html" (
    echo Frontend build not found. Please run:
    echo cd /d "%~dp0frontend"
    echo npm install
    echo npm run build
    pause
    exit /b 1
)

call run.bat
