@echo off
setlocal EnableExtensions
rem Force UTF-8 code page so Chinese log text is not garbled
chcp 65001 >nul
title GreyDrive Server - logs: data\logs\greydrive.log

rem Always run from the folder containing this script and the jar
cd /d "%~dp0"

set "JAR=GreyDrive.jar"
if not exist "%JAR%" (
    echo [ERROR] %JAR% not found. Put it in the same folder as this script.
    pause
    exit /b 1
)

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Install JDK 21+ and set JAVA_HOME or PATH.
    echo         https://adoptium.net/temurin/releases/?version=21
    pause
    exit /b 1
)

rem Require Java 21 or newer
"%JAVA_EXE%" -version 2>"%TEMP%\greydrive_java_version.txt"
set "JVER="
for /f "tokens=3" %%v in ('type "%TEMP%\greydrive_java_version.txt" ^| findstr /i "version"') do set "JVER=%%v"
del "%TEMP%\greydrive_java_version.txt" >nul 2>&1
set "JVER=%JVER:"=%"
set "JMAJOR=%JVER%"
for /f "delims=.- tokens=1" %%m in ("%JVER%") do set "JMAJOR=%%m"
if "%JMAJOR%"=="1" for /f "delims=.- tokens=2" %%m in ("%JVER%") do set "JMAJOR=%%m"
if not defined JMAJOR set "JMAJOR=0"
if %JMAJOR% LSS 21 (
    echo [ERROR] JDK 21 or newer is required. Current: %JVER%
    pause
    exit /b 1
)

rem GC: serial = lowest memory footprint; zgc = lowest pause but more RSS.
rem Set GREYDRIVE_GC=zgc to enable generational ZGC (Java 21/22 need the extra flag).
if not defined JAVA_OPTS set "GC_MODE=serial"
if not defined JAVA_OPTS if defined GREYDRIVE_GC set "GC_MODE=%GREYDRIVE_GC%"
if not defined JAVA_OPTS set "GC_OPTS=-XX:+UseSerialGC"
if not defined JAVA_OPTS if /i "%GC_MODE%"=="zgc" set "GC_OPTS=-XX:+UseZGC"
if not defined JAVA_OPTS if /i "%GC_MODE%"=="zgc" if %JMAJOR% LSS 23 set "GC_OPTS=%GC_OPTS% -XX:+ZGenerational"
if not defined JAVA_OPTS set "JAVA_OPTS=-Xms64m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=128m %GC_OPTS%"

rem Keep data persistent when launched from a self-extracting package (temp dir)
set "DATA_HOME=%LOCALAPPDATA%\GreyDrive\data"
if not exist "%DATA_HOME%" mkdir "%DATA_HOME%" >nul 2>&1
if not exist "data" mklink /J "data" "%DATA_HOME%" >nul 2>&1
if not exist "data" (
    echo [WARN] Could not create the persistent data link. Data stays under: %CD%\data
)

rem Prepare log dir and file so the log window can start immediately
set "LOGDIR=%CD%\data\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1
set "LOG=%LOGDIR%\greydrive.log"
set "CONSOLE_LOG=%LOGDIR%\console.log"
if not exist "%LOG%" type nul > "%LOG%"
if not exist "%CONSOLE_LOG%" type nul > "%CONSOLE_LOG%"

echo.
echo   ______                          ______            _
echo  /  ___/  ______   __  __  ____  / / _ \   _  __   (_)   _____    ___
echo /   \  \  \_  __ \ / / / /  / _/ / /  __/ ^| ^|/ /  / /   / / _ \  / _ \
echo \    \  \  ^|  ^| \/ / /_/ / / /_/ / /_/ /  ^| ^|/ /  / /   / /  __/ /  __/
echo  \______/  ^|__^|    \__, / /____/  \____/  ^|___/_ /_/    \___/  \___/
echo                    /____/
echo.
echo  :: GreyDrive Server :: by GreyZ
echo.
echo ==========================================
echo   GreyDrive starting...  Press Ctrl+C to stop
echo   Java : %JVER%
echo   Data : %CD%\data
echo   Log  : %LOG%
echo   Logs : live output shown in the "GreyDrive Logs" window
echo ==========================================
echo.

rem Open a separate log window so Spring output never covers the banner
if not defined GREYDRIVE_NO_LOG_WINDOW (
    start "GreyDrive Logs" powershell -NoExit -Command "[Console]::OutputEncoding=[Text.Encoding]::UTF8; chcp 65001 > $null; Get-Content -LiteralPath '%LOG%' -Encoding UTF8 -Wait -Tail 60"
)

rem Watch the log: print access URLs first, then Done!(xx.xxs)
start "" /b powershell -NoProfile -ExecutionPolicy Bypass -Command "$f='%LOG%'; $fs=[IO.File]::Open($f,'Open','Read','ReadWrite'); $fs.Seek(0,'End')|Out-Null; $sr=New-Object IO.StreamReader($fs,[Text.Encoding]::UTF8); $sw=[Diagnostics.Stopwatch]::StartNew(); $ok=$false; while($sw.Elapsed.TotalSeconds -lt 180){ $line=$sr.ReadLine(); if($null -eq $line){ Start-Sleep -Milliseconds 200; continue }; if($line -match 'Started GreyDriveApplication in ([0-9]+(?:\.[0-9]+)?) seconds'){ $sec=(([double]$Matches[1]).ToString('0.00')); Start-Sleep -Milliseconds 1200; Get-Content -LiteralPath $f -Encoding UTF8 -Tail 40 | Where-Object { $_ -match '(\u670D\u52A1/API)|(\u7BA1\u7406\u540E\u53F0)|Swagger:|OpenAPI:' } | Select-Object -Last 4 | ForEach-Object { Write-Host $_ }; Write-Host ('Done!(' + $sec + 's)') -ForegroundColor Green; $ok=$true; break } }; if(-not $ok){ Write-Host '[WARN] startup message not detected within 180s.' -ForegroundColor Yellow }; $sr.Dispose(); $fs.Dispose()"

rem Keep the banner visible: Java stdout goes to a separate capture file
"%JAVA_EXE%" %JAVA_OPTS% -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%JAR%" %* >> "%CONSOLE_LOG%" 2>&1
set "EXITCODE=%errorlevel%"

echo.
if not "%EXITCODE%"=="0" (
    echo [ERROR] GreyDrive exited with code %EXITCODE%. See the log: "%LOG%"
) else (
    echo GreyDrive stopped.
)
pause
exit /b %EXITCODE%
