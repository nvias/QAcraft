@echo off
set MAVEN_VERSION=3.9.14
set WRAPPER_DIR=%~dp0.mvn\wrapper
set MAVEN_DIR=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
set MAVEN_ZIP=%WRAPPER_DIR%\maven.zip
set MAVEN_URL=https://dlcdn.apache.org/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip

if exist "%MAVEN_DIR%\bin\mvn.cmd" goto run

echo.
echo Downloading Maven %MAVEN_VERSION%...
echo.
if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%MAVEN_ZIP%'"

if not exist "%MAVEN_ZIP%" (
    echo.
    echo ERROR: Download failed!
    echo Please download Maven manually from:
    echo   https://maven.apache.org/download.cgi
    echo Extract to: %WRAPPER_DIR%\
    echo.
    pause
    exit /b 1
)

echo Extracting...
powershell -Command "Expand-Archive -Path '%MAVEN_ZIP%' -DestinationPath '%WRAPPER_DIR%' -Force"
del "%MAVEN_ZIP%" 2>nul
echo Maven %MAVEN_VERSION% ready!
echo.

:run
"%MAVEN_DIR%\bin\mvn.cmd" %*
