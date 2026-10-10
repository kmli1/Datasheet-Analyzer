@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME (
    if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    )
)
echo Android Studio ile projeyi en az bir kez acip SDK yolunu ve Gradle Sync islemini tamamlayin.
echo Debug APK derleniyor...
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
    echo Derleme basarisiz. Yukaridaki ilk hata mesajini kontrol edin.
    pause
    exit /b 1
)
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk
start "" explorer.exe "%CD%\app\build\outputs\apk\debug"
pause
endlocal
