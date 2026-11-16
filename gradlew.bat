@echo off
setlocal
set "GRADLE_VERSION=9.2.1"
set "DIST_NAME=gradle-%GRADLE_VERSION%-bin.zip"
set "DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "CACHE_ROOT=%GRADLE_USER_HOME%\wrapper\dists\fhasix-gradle-%GRADLE_VERSION%"
set "GRADLE_HOME_LOCAL=%CACHE_ROOT%\gradle-%GRADLE_VERSION%"
if exist "%GRADLE_HOME_LOCAL%\bin\gradle.bat" goto runGradle
where curl >nul 2>nul || (echo Missing curl. Install a recent Windows version or Gradle manually.& exit /b 2)
where powershell >nul 2>nul || (echo Missing PowerShell.& exit /b 2)
if not exist "%CACHE_ROOT%" mkdir "%CACHE_ROOT%"
set "TMP_DIR=%TEMP%\fhasix-gradle-%RANDOM%"
mkdir "%TMP_DIR%"
echo Downloading official Gradle %GRADLE_VERSION% (first run only; about 130 MB)...
curl --fail --location --retry 3 "%DIST_URL%" -o "%TMP_DIR%\%DIST_NAME%" || (rmdir /s /q "%TMP_DIR%" & exit /b 3)
curl --fail --location "%DIST_URL%.sha256" -o "%TMP_DIR%\checksum.txt" || (rmdir /s /q "%TMP_DIR%" & exit /b 3)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$expected=(Get-Content -Raw '%TMP_DIR%\checksum.txt').Trim(); $actual=(Get-FileHash '%TMP_DIR%\%DIST_NAME%' -Algorithm SHA256).Hash.ToLower(); if ($expected -ne $actual) { exit 1 }" || (echo ERROR: Gradle checksum verification failed.& rmdir /s /q "%TMP_DIR%" & exit /b 4)
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%TMP_DIR%\%DIST_NAME%' -DestinationPath '%TMP_DIR%' -Force" || (rmdir /s /q "%TMP_DIR%" & exit /b 3)
move "%TMP_DIR%\gradle-%GRADLE_VERSION%" "%GRADLE_HOME_LOCAL%" >nul || (rmdir /s /q "%TMP_DIR%" & exit /b 3)
rmdir /s /q "%TMP_DIR%"
:runGradle
call "%GRADLE_HOME_LOCAL%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
