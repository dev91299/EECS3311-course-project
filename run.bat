@echo off
REM Starts the AI Code Review Agent on Windows.
REM
REM   run.bat                                   graphical interface
REM   run.bat --cli                             command-line interface
REM   run.bat --cli --repo samples\demo-project

setlocal
cd /d "%~dp0"

if not exist target\classes (
    echo Not built yet - building first.
    call build.bat
    echo.
)

if not exist .env (
    echo NOTE: no .env file found, so the application will start in OFFLINE MODE.
    echo       Copy .env.example to .env and add your API key for the full system.
    echo.
)

java -cp target\classes com.eecs3311.codereview.app.Main %*
endlocal
