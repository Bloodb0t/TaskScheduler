@echo off
setlocal EnableDelayedExpansion

REM =========================================================================
REM  Task Scheduler — One-click EXE installer builder
REM  Requires:
REM    * JDK 21+ on PATH (with jpackage tool — bundled since JDK 14)
REM    * Apache Maven (mvn) on PATH
REM    * Inno Setup 6 — required for jpackage --type EXE on Windows
REM        Download: https://jrsoftware.org/isdl.php  (innosetup-6.x.x.exe)
REM        After install, ensure "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" exists
REM        (jpackage auto-discovers it from registry)
REM    * Optional alternative: WiX Toolset v3 — produces MSI if you prefer
REM =========================================================================

echo.
echo ============================================================
echo   Task Scheduler — Building EXE installer
echo ============================================================
echo.

REM --- Pre-flight checks --------------------------------------------------
set "HAS_ISS="
if exist "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" set "HAS_ISS=1"
if exist "C:\Program Files\Inno Setup 6\ISCC.exe" set "HAS_ISS=1"

where /q java
if errorlevel 1 (
    echo [ERROR] java.exe not found on PATH. Install JDK 21+ first.
    echo         Download: https://adoptium.net/temurin/releases/?version=21
    goto :END
)
where /q mvn
if errorlevel 1 (
    echo [ERROR] mvn.exe not found on PATH. Install Apache Maven first.
    echo         Download: https://maven.apache.org/download.cgi
    goto :END
)
where /q jpackage
if errorlevel 1 (
    echo [ERROR] jpackage.exe not found on PATH.
    echo         JDK 14+ with jpackage is required; install JDK 21 and retry.
    goto :END
)
if not defined HAS_ISS (
    echo [WARN]  Inno Setup 6 not detected at its default install path.
    echo         Install Inno Setup from: https://jrsoftware.org/isdl.php
    echo         (Press Ctrl+C now to cancel, or Enter to continue anyway — likely will fail later)
    echo.
    pause
)

set "JAVA_OUT="
for /f "tokens=*" %%a in ('java -version 2^>^&1 ^| head -1') do set "JAVA_OUT=%%a"
if "%JAVA_OUT%"=="" for /f "tokens=*" %%a in ('java -version 2^>^&1') do (
    set "JAVA_OUT=%%a"
    goto :JAVA_PRINTED
)
:JAVA_PRINTED
echo [OK]  java     : %JAVA_OUT%
for /f "tokens=*" %%a in ('jpackage --version 2^>^&1') do echo [OK]  jpackage: %%a
for /f "tokens=*" %%a in ('mvn -v 2^>^&1 ^| findstr "Apache Maven"') do echo [OK]  mvn      : %%a
echo.

REM --- Step 1/2: compile + package via Maven ------------------------------
echo === Step 1/2: mvn clean package (compiles + runs jpackage) ===
echo.
set "LOG=%~dp0build-installer.log"
del /q "%LOG%" 2>nul
call mvn clean package -DskipTests >"%LOG%" 2>&1
set MVN_ERR=%ERRORLEVEL%

echo.
if %MVN_ERR% NEQ 0 (
    echo [ERROR] mvn package FAILED with exit code %MVN_ERR%
    echo         Full log saved to: "%LOG%"
    echo.
    echo --------- Last 30 lines of log ---------
    powershell -NoProfile -Command "Get-Content '%LOG%' -Tail 30"
    echo -----------------------------------------
    goto :END
)
echo [OK] mvn package (exit %MVN_ERR%)

REM --- Step 2/2: locate installer ----------------------------------------
echo.
echo === Step 2/2: Locate installer ===
echo.
set "OUT_DIR=%~dp0target\dist"
if not exist "%OUT_DIR%" (
    echo [WARN] Output directory "%OUT_DIR%" not found.
    echo        jpackage plugin may not have run; check above log lines for "BUILD SUCCESS" and "jpackage:jpackage".
    goto :END
)

set "FOUND="
for %%f in ("%OUT_DIR%\*.exe" "%OUT_DIR%\*.msi") do (
    echo   - %%~ff
    set "FOUND=1"
)
if not defined FOUND (
    echo [WARN] No .exe or .msi was produced under "%OUT_DIR%".
    echo        Common cause: Inno Setup ISCC.exe was not on PATH while jpackage ran.
) else (
    echo.
    echo Produced installer(s) are in: "%OUT_DIR%"
)

:END
echo.
echo Press any key to exit...
pause >nul
endlocal
