@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND_PORT=8090"

echo Starting Tally Integration in development mode...

start "Tally Backend" "%ComSpec%" /k "set BACKEND_PORT=%BACKEND_PORT% && cd /d ""%ROOT%backend"" && call run.bat"
start "Tally Frontend" "%ComSpec%" /k "set BACKEND_PORT=%BACKEND_PORT% && cd /d ""%ROOT%frontend"" && npm.cmd run dev"

echo.
echo Backend window and frontend window have been opened.
echo New backend port: %BACKEND_PORT%
echo Open the Vite URL shown in the frontend window, usually http://localhost:5173
