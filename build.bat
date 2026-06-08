@echo off
echo ====================================
echo   Building QAcraft Plugin
echo ====================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java not found!
    echo Install Java 21+ from: https://adoptium.net/
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
    echo Plugin JAR: target\QAcraft.jar
    echo Copy it into your server's plugins\ folder.
    echo.
) else (
    echo ====================================
    echo   BUILD FAILED
    echo ====================================
    echo Check the errors above.
)
echo.
pause
