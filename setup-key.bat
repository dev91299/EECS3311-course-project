@echo off
REM Creates your local .env file and puts your API key in it.
REM
REM Run it by double-clicking setup-key.bat, or typing  setup-key.bat  in a terminal.
REM
REM Why this exists: .env starts with a dot, so File Explorer hides it by default and it
REM is easy to think the file was never created. This script creates it, writes your key
REM into it, checks the key actually works, and confirms Git is ignoring it - without you
REM having to find the file at all.

setlocal enabledelayedexpansion
cd /d "%~dp0"

echo.
echo ==============================================
echo   AI Code Review Agent - API key setup
echo ==============================================
echo.

REM --------------------------------------------------------------- create the file

if not exist ".env.example" (
    echo ERROR: .env.example is missing.
    echo Are you running this from inside the project folder?
    echo You are currently in: %cd%
    echo.
    pause
    exit /b 1
)

if exist ".env" (
    echo You already have a .env file.
    echo.
    set /p REPLY="Replace the key in it? [y/N]: "
    if /i not "!REPLY!"=="y" (
        echo Left unchanged. Nothing to do.
        echo.
        pause
        exit /b 0
    )
    echo.
) else (
    copy /y ".env.example" ".env" >nul
    echo Created your .env file from the template.
    echo.
)

REM --------------------------------------------------------------- ask for the key

echo Now paste your API key.
echo.
echo   Don't have one yet? Get a free one, no credit card needed:
echo     1. Go to  https://console.groq.com
echo     2. Sign up, then click 'API Keys' on the left
echo     3. Click 'Create API Key' and copy it
echo.
echo   To paste into this window: right-click, or press Ctrl+V.
echo.
set /p API_KEY="Paste your key and press Enter: "
echo.

if "%API_KEY%"=="" (
    echo Nothing was pasted. Run setup-key.bat again when you have your key.
    echo.
    pause
    exit /b 1
)

REM --------------------------------------------------------------- write it in

REM Rewrite the LLM_API_KEY line, leaving every other setting and comment alone.
set "FOUND="
if exist ".env.tmp" del ".env.tmp"
for /f "usebackq delims=" %%L in (".env") do (
    set "LINE=%%L"
    set "PREFIX=!LINE:~0,12!"
    if "!PREFIX!"=="LLM_API_KEY=" (
        echo LLM_API_KEY=%API_KEY%>> ".env.tmp"
        set "FOUND=1"
    ) else (
        echo !LINE!>> ".env.tmp"
    )
)
if not defined FOUND echo LLM_API_KEY=%API_KEY%>> ".env.tmp"
move /y ".env.tmp" ".env" >nul

echo Saved your key to .env
echo.

REM --------------------------------------------------------------- safety check

where git >nul 2>nul
if not errorlevel 1 (
    if exist ".git" (
        git check-ignore -q .env 2>nul
        if errorlevel 1 (
            echo [!!] WARNING: Git is NOT ignoring .env. Do not commit until this is fixed.
            echo      Check that .gitignore contains a line reading: .env
        ) else (
            echo [OK] Git is ignoring .env, so your key can never be committed by accident.
        )
        echo.
    )
)

REM --------------------------------------------------------------- does it work?

echo Checking that the key works...
echo.

if not exist "target\classes" (
    echo The application is not built yet, so let me build it first.
    call build.bat >nul 2>nul
    if errorlevel 1 (
        echo The build failed. Run build.bat on its own to see why.
        echo.
        pause
        exit /b 1
    )
)

java -cp target\classes com.eecs3311.codereview.app.Main --cli --repo samples\demo-project --command "summarize PriceCalculator.java" > "%TEMP%\keycheck.txt" 2>&1

REM The checks below are ordered deliberately: a network problem must be reported as a
REM network problem, not as a bad key, or someone behind a firewall will waste time
REM regenerating a key that was fine all along.

findstr /C:"OFFLINE STRUCTURAL SUMMARY" "%TEMP%\keycheck.txt" >nul
if not errorlevel 1 (
    echo [!!] The key is not being picked up - the app is still in OFFLINE MODE.
    echo.
    echo      Things to check:
    echo        - Is .env in this folder? Run:  dir /a ^| findstr env
    echo        - Does the line read  LLM_API_KEY=gsk_...  with no spaces around the = ?
    echo        - Run setup-key.bat again and re-paste the key.
    del "%TEMP%\keycheck.txt"
    echo.
    pause
    exit /b 1
)

findstr /I /C:"Network error contacting" "%TEMP%\keycheck.txt" >nul
if not errorlevel 1 (
    echo [??] Could not reach the provider, so the key could not be checked.
    echo.
    echo      Your key has still been saved. This is a connection problem, not a key
    echo      problem - do NOT regenerate your key.
    echo.
    echo      Things to check:
    echo        - Are you online?
    echo        - Are you on a school or work network that blocks outside sites?
    echo          Try again on a home network or a phone hotspot.
    del "%TEMP%\keycheck.txt"
    echo.
    pause
    exit /b 1
)

findstr /I /C:"API key was rejected" "%TEMP%\keycheck.txt" >nul
if not errorlevel 1 (
    echo [!!] The provider rejected the key.
    echo.
    echo      It may be mistyped, or it may have been deleted at the console.
    echo      Make a new one at https://console.groq.com and run setup-key.bat again.
    del "%TEMP%\keycheck.txt"
    echo.
    pause
    exit /b 1
)

findstr /I /C:"model name was not found" "%TEMP%\keycheck.txt" >nul
if not errorlevel 1 (
    echo [!!] The key works, but the model name is wrong.
    echo.
    echo      The free model may have been renamed. Open https://console.groq.com,
    echo      look at the model list, and add a line to your .env file like:
    echo        LLM_MODEL=the-current-model-name
    del "%TEMP%\keycheck.txt"
    echo.
    pause
    exit /b 1
)

echo [OK] The key works. Here is the model's reply to a real request:
echo.
powershell -NoProfile -Command "Get-Content '%TEMP%\keycheck.txt' | Select-Object -Last 12 | ForEach-Object { '     ' + $_ }" 2>nul
del "%TEMP%\keycheck.txt"

echo.
echo ==============================================
echo   Setup complete.
echo.
echo   Start the app with:
echo     run.bat            graphical interface
echo     run.bat --cli      command line
echo ==============================================
echo.
pause
endlocal
