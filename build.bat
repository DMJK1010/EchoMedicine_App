@echo off
echo [1/2] Building release APK...
call gradlew.bat assembleRelease
if %errorlevel% neq 0 (
    echo Build failed.
    pause
    exit /b 1
)