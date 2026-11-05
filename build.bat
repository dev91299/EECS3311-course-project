@echo off
REM Builds the AI Code Review Agent on Windows, without Maven.
REM The project has no runtime dependencies, so a plain JDK is enough.
REM
REM   build.bat          compile the application
REM   build.bat --check  compile, then run the verification harness

setlocal enabledelayedexpansion
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo ERROR: javac was not found.
    echo Install a Java Development Kit ^(version 17 or newer^) from https://adoptium.net
    echo then close and reopen this window.
    exit /b 1
)

echo Compiling...
if exist target\classes rmdir /s /q target\classes
mkdir target\classes

dir /s /b src\main\java\*.java > target\sources.txt
javac -encoding UTF-8 -d target\classes @target\sources.txt
if errorlevel 1 (
    echo.
    echo BUILD FAILED. See the messages above.
    exit /b 1
)

echo Build succeeded. Classes are in target\classes

if "%~1"=="--check" (
    echo.
    echo Running the verification harness...
    if not exist target\tools mkdir target\tools
    javac -encoding UTF-8 -cp target\classes -d target\tools tools\SelfCheck.java
    java -cp "target\classes;target\tools" SelfCheck samples\demo-project
)

echo.
echo Next steps:
echo   run.bat            start the graphical interface
echo   run.bat --cli      start the command-line interface
endlocal
