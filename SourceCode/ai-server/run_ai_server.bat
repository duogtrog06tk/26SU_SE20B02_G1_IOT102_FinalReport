@echo off
title KidCheck AI Server
echo ===================================================
echo =       KidCheck AI Server Auto-Runner            =
echo ===================================================
echo.

cd /d "%~dp0"

IF NOT EXIST "venv\Scripts\activate.bat" (
    echo [INFO] Dang tao moi truong ao Python ^(venv^)...
    python -m venv venv
    IF %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Khong the tao venv. Vui long kiem tra lai Python da duoc cai dat hay chua.
        pause
        exit /b %ERRORLEVEL%
    )
)

echo [INFO] Kich hoat moi truong ao...
call venv\Scripts\activate.bat

echo [INFO] Dang kiem tra va cai dat thu vien (neu thieu)...
pip install -r requirements.txt

echo.
echo [INFO] Dang khoi dong AI Server...
python app.py

pause
