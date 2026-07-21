@echo off
title KidCheck AI Server Launcher
color 0A
echo ===================================================
echo           KIDCHECK AI SERVER LAUNCHER
echo ===================================================
echo.
echo [1/2] Changing directory to AI Server folder...
cd /d "%~dp0"

echo [2/2] Starting AI Server...
echo.

if exist "venv\Scripts\activate.bat" (
    call "venv\Scripts\activate.bat"
)

python app.py

if errorlevel 1 (
    echo.
    color 0C
    echo [ERROR] AI Server closed with an error!
) else (
    echo.
    echo [INFO] AI Server stopped.
)
echo.
pause
