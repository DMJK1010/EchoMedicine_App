@echo off
echo [1/2] Building debug APK...
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo Build failed.
    pause
    exit /b 1
)
echo [2/2] Done! APK: app\build\outputs\apk\debug\app-debug.apk
pause
