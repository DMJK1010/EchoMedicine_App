@echo off
echo [1/2] Building release APK...
call gradlew.bat assembleRelease
if %errorlevel% neq 0 (
    echo Build failed.
    pause
    exit /b 1
)

echo [2/2] Installing to device...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\release\app-release.apk"
if %errorlevel% neq 0 (
    echo Install failed. Check device connection.
    pause
    exit /b 1
)

echo Done!
pause
