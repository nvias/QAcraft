@echo off
echo ====================================
echo   Building QAcraft Plugin
echo ====================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java not found!
    echo Install Java 25 or newer from: https://adoptium.net/
    echo.
    pause
    exit /b 1
)

echo Java found:
java -version 2>&1 | findstr /i "version"
echo.

call "%~dp0mvnw.cmd" clean package -q
echo.

if exist "%~dp0target\QAcraft.jar" (
    echo ====================================
    echo   BUILD SUCCESSFUL!
    echo ====================================
    echo.
    echo Plugin: target\QAcraft.jar
    echo.
    set /p COPY="Copy to QAcraft-Server\plugins\? (Y/N): "
    if /i "%COPY%"=="Y" (
        if not exist "%~dp0QAcraft-Server\plugins" mkdir "%~dp0QAcraft-Server\plugins"
        copy "%~dp0target\QAcraft.jar" "%~dp0QAcraft-Server\plugins\" >nul
        echo Copied to QAcraft-Server\plugins\!
    )
) else (
    echo ====================================
    echo   BUILD FAILED
    echo ====================================
    echo Check errors above.
)
echo.
pause
