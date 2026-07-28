@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ========================================
echo   美乐蒂猜猜乐 - APK 构建脚本
echo ========================================
echo.

:: 设置项目目录
set "PROJECT_DIR=%~dp0"
set "ANDROID_SDK_ROOT=%PROJECT_DIR%android-sdk"
set "JAVA_HOME=C:\Program Files\Java\jdk-17"

:: 检查Java 17
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [!] 未找到 Java 17，正在检查其他位置...
    
    :: 检查常见JDK 17安装位置
    for %%j in (
        "C:\Program Files\Eclipse Adoptium\jdk-17*"
        "C:\Program Files\Microsoft\jdk-17*"
        "C:\Program Files\Java\jdk-17*"
        "C:\Program Files\Amazon Corretto\jdk17*"
        "%USERPROFILE%\.jdks\*"
    ) do (
        if exist %%~sj\bin\java.exe (
            set "JAVA_HOME=%%~sj"
            echo [✓] 找到 Java: !JAVA_HOME!
            goto :found_java
        )
    )
    
    echo.
    echo [错误] 需要安装 JDK 17 才能构建此项目！
    echo 请从以下地址下载安装 JDK 17：
    echo   https://adoptium.net/temurin/releases/?version=17
    echo   或 https://www.microsoft.com/openjdk
    echo.
    pause
    exit /b 1
)
:found_java

echo [✓] 使用 Java: %JAVA_HOME%
echo [✓] 项目目录: %PROJECT_DIR%
echo [✓] Android SDK: %ANDROID_SDK_ROOT%
echo.

:: 创建SDK目录
if not exist "%ANDROID_SDK_ROOT%" mkdir "%ANDROID_SDK_ROOT%"

:: 检查是否需要下载命令行工具
set "CMDLINE_TOOLS=%ANDROID_SDK_ROOT%\cmdline-tools"
if not exist "%CMDLINE_TOOLS%\latest\bin\sdkmanager.bat" (
    echo [*] 正在下载 Android 命令行工具...
    
    :: 下载命令行工具
    set "CMDLINE_URL=https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    set "ZIP_FILE=%PROJECT_DIR%\cmdline-tools.zip"
    
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%CMDLINE_URL%' -OutFile '%ZIP_FILE%'" 2>nul
    
    if not exist "%ZIP_FILE%" (
        echo [错误] 下载失败！请手动下载：
        echo %CMDLINE_URL%
        echo 解压到 %CMDLINE_TOOLS%\latest\
        pause
        exit /b 1
    )
    
    echo [*] 正在解压...
    powershell -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%CMDLINE_TOOLS%' -Force"
    
    :: 移动到正确的目录结构
    if exist "%CMDLINE_TOOLS%\cmdline-tools" (
        move "%CMDLINE_TOOLS%\cmdline-tools" "%CMDLINE_TOOLS%\latest_temp" >nul 2>&1
        rmdir /s /q "%CMDLINE_TOOLS%\latest" 2>nul
        ren "%CMDLINE_TOOLS%\latest_temp" "latest" >nul 2>&1
    )
    
    del "%ZIP_FILE%" 2>nul
    echo [✓] 命令行工具安装完成
)

:: 设置环境变量
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "PATH=%CMDLINE_TOOLS%\latest\bin;%PATH%"
set "PATH=%ANDROID_SDK_ROOT%\platform-tools;%PATH%"
set "PATH=%ANDROID_SDK_ROOT%\build-tools\34.0.0;%PATH%"

:: 安装必要的SDK组件
echo.
echo [*] 正在检查/安装 SDK 组件...
call sdkmanager --list 2>nul | findstr "installed" >nul 2>&1
if errorlevel 1 (
    echo [*] 首次运行，需要接受许可协议...
    yes | sdkmanager --licenses >nul 2>&1
)

:: 安装必要组件（静默模式）
echo [*] 安装 SDK 平台和构建工具...
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools" >nul 2>&1

echo [✓] SDK 组件就绪
echo.

:: 下载Gradle Wrapper
echo [*] 准备 Gradle 构建...
if not exist "%PROJECT_DIR%\gradlew.bat" (
    echo [*] 生成 gradlew 脚本...
    
    :: 创建gradlew wrapper properties
    if not exist "%PROJECT_DIR%\gradle\wrapper" mkdir "%PROJECT_DIR%\gradle\wrapper"
    
    :: 下载gradle-wrapper.jar
    set "GRADLE_WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.2.0/gradle/wrapper/gradle-wrapper.jar"
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%GRADLE_WRAPPER_URL%' -OutFile '%PROJECT_DIR%\gradle\wrapper\gradle-wrapper.jar'" 2>nul
)

:: 创建gradlew.bat
if not exist "%PROJECT_DIR%\gradlew.bat" (
    call :create_gradlew_bat
)

echo.
echo ========================================
echo   开始构建 Release APK...
echo ========================================
echo.

:: 执行Gradle构建
call gradlew.bat assembleRelease --no-daemon --stacktrace

if errorlevel 1 (
    echo.
    echo [错误] 构建失败！请检查上面的错误信息。
    pause
    exit /b 1
)

echo.
echo ========================================
echo   构建成功！
echo ========================================
echo.
echo APK 文件位置:
dir /s /b "%PROJECT_DIR%\app\build\outputs\apk\release\*.apk" 2>nul

echo.
echo 按任意键打开APK所在文件夹...
pause >nul
explorer "%PROJECT_DIR%\app\build\outputs\apk\release"

exit /b 0

:create_gradlew_bat
(
echo @echo off
echo setlocal
echo.
echo set DIRNAME=%~dp0
echo if "%DIRNAME%"=="" set DIRNAME=.
echo.
echo set APP_BASE_NAME=%~n0
echo set APP_HOME=%DIRNAME%
echo.
echo call :find_java_from_java_home
echo.
echo set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
echo.
echo set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
echo.
echo "%JAVA_EXE%" ^%^%DEFAULT_JVM_OPTS^%^% JAVA_OPTS^%^% GRADLE_OPTS^%^% -classpath "^%%CLASSPATH^%%" org.gradle.wrapper.GradleWrapperMain ^%%*
echo.
echo endlocal
echo goto :eof
echo.
echo :find_java_from_java_home
echo set JAVA_HOME=%JAVA_HOME%
echo.
echo for %%i in ^("%JAVA_HOME%\bin\java.exe"^) do set "JAVA_EXE=%%~fi"
echo goto :eof
) > "%PROJECT_DIR%\gradlew.bat"
goto :eof
