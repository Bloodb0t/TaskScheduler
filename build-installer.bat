@echo off
setlocal EnableExtensions DisableDelayedExpansion

REM =========================================================================
REM  Task Scheduler - One-click build script
REM  Produces (in this order):
REM    1) Portable app folder - always works, no extra tools needed
REM       target\dist-portable\Task Scheduler\Task Scheduler.exe
REM
REM    2) Windows EXE installer  (requires WiX Toolset v3 - see below)
REM       target\dist\Task Scheduler-1.0.0.exe
REM
REM  Required tools (all resolved automatically from PATH / known locations):
REM    * JDK 21+ full install, with jpackage.exe (bundled since JDK 14)
REM        Install: winget install EclipseAdoptium.Temurin.21.JDK
REM        or     : https://adoptium.net/temurin/releases/?version=21
REM
REM    * Apache Maven 3.9+
REM        Install: winget install Apache.Maven
REM        or     : https://maven.apache.org/download.cgi
REM
REM    * WiX Toolset v3  (light.exe + candle.exe)   [only for EXE installer]
REM        NOTE: Oracle JDK 23+ jpackage dropped Inno Setup support.
REM              To build a .exe installer, WiX Toolset v3 is now required.
REM        Install: winget install WiXToolset.wix3
REM        Download: https://wixtoolset.org/docs/wix3/
REM =========================================================================

echo.
echo ============================================================
echo   Task Scheduler - Building double-clickable package(s)
echo ============================================================
echo.

REM --- 1) Resolve real JDK bin dir from JAVA_HOME or java.exe ---------------
set "JDK_BIN="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JDK_BIN=%JAVA_HOME%\bin"
    )
)
if not defined JDK_BIN (
    for /f "tokens=*" %%p in ('where java.exe 2^>nul') do (
        call :resolveJdkBinFromExe "%%~dp$PATH:p"
    )
)
if not defined JDK_BIN (
    for /d %%d in ("C:\Program Files\Java\jdk-*") do set "JDK_BIN=%%d\bin"
)
if defined JDK_BIN (
    set "PATH=%JDK_BIN%;%PATH%"
    echo [OK]  JDK bin   : %JDK_BIN%
) else (
    echo [WARN]  Could not resolve a real JDK bin directory.
    echo         (If only Oracle javapath shortcuts are on PATH, jpackage.exe might be missing.)
    echo         Setting JAVA_HOME to your actual JDK root folder is the safest fix.
    echo.
)

REM --- 2) Resolve WiX Toolset v3  (light.exe + candle.exe) -----------------
set "WIX_BIN="
where /q light.exe
if not errorlevel 1 (
    where /q candle.exe
    if not errorlevel 1 set "WIX_BIN=__PATH__"
)
if "%WIX_BIN%"=="" (
    if exist "%LOCALAPPDATA%\Programs\WiX Toolset v3\bin\light.exe" (
        if exist "%LOCALAPPDATA%\Programs\WiX Toolset v3\bin\candle.exe" (
            set "WIX_BIN=%LOCALAPPDATA%\Programs\WiX Toolset v3\bin"
        )
    )
)
if "%WIX_BIN%"=="" (
    for /d %%d in ("C:\Program Files (x86)\WiX Toolset v3.*") do (
        if exist "%%d\bin\light.exe" (
            if exist "%%d\bin\candle.exe" (
                set "WIX_BIN=%%d\bin"
            )
        )
    )
)
if "%WIX_BIN%"=="" (
    for /d %%d in ("C:\Program Files\WiX Toolset v3.*") do (
        if exist "%%d\bin\light.exe" (
            if exist "%%d\bin\candle.exe" (
                set "WIX_BIN=%%d\bin"
            )
        )
    )
)
if "%WIX_BIN%"=="__PATH__" (
    echo [OK]  WiX Toolset: already on PATH (light.exe + candle.exe found)
    set "WIX_BIN="
) else (
    if defined WIX_BIN (
        set "PATH=%WIX_BIN%;%PATH%"
        echo [OK]  WiX Toolset: %WIX_BIN%
    ) else (
        echo [INFO] WiX Toolset v3 not installed.
        echo        Will produce the portable APP_IMAGE only; EXE installer build will be skipped.
        echo        To get a one-click .exe installer too, run:
        echo            winget install WiXToolset.wix3
        echo        then re-run this script.
        echo.
    )
)

REM --- 3) Pre-flight checks ------------------------------------------------
where /q java.exe
if errorlevel 1 (
    echo [ERROR] java.exe not found. Install JDK 21+ and add it to PATH or set JAVA_HOME.
    echo         winget install EclipseAdoptium.Temurin.21.JDK
    goto :END
)
where /q mvn.cmd
if errorlevel 1 (
    where /q mvn
    if errorlevel 1 (
        echo [ERROR] mvn not found. Install Apache Maven and add its bin folder to PATH.
        echo         winget install Apache.Maven
        goto :END
    )
)
where /q jpackage.exe
if errorlevel 1 (
    echo [ERROR] jpackage.exe not found.
    echo         It lives inside the JDK bin folder.
    if defined JDK_BIN (echo           JDK_BIN=%JDK_BIN%) else (echo           (no real JDK bin was resolved - install a full JDK, not a JRE.)
    )
    echo         Install a full JDK (not just a JRE) version 21 or newer.
    goto :END
)

REM --- 4) Print toolchain versions ---------------------------------------
set "JAVA_OUT="
for /f "tokens=*" %%a in ('java -version 2^>^&1') do (
    set "JAVA_OUT=%%a"
    goto :JAVA_PRINTED
)
:JAVA_PRINTED
echo [OK]  java     : %JAVA_OUT%
for /f "tokens=*" %%a in ('jpackage --version 2^>^&1') do echo [OK]  jpackage: %%a
for /f "tokens=*" %%a in ('mvn -v 2^>^&1 ^| findstr /c:"Apache Maven"') do echo [OK]  mvn      : %%a
echo.

REM --- 5) Maven: clean package (runs APP_IMAGE then EXE) -----------------
echo === Step 1/2: mvn clean package ===
echo.

set "LOG=%~dp0build-installer.log"
del /q "%LOG%" 2>nul

if defined WIX_BIN (
    REM Full build: portable + EXE installer
    call mvn clean package -DskipTests >"%LOG%" 2>&1
) else (
    REM No WiX: only build the portable app image (no external packaging tool needed)
    call mvn clean package -DskipTests '-Djpackage.package-exe.skip=true' >"%LOG%" 2>&1
)
set "MVN_ERR=%ERRORLEVEL%"

echo.
if "%MVN_ERR%" NEQ "0" (
    echo [ERROR] Maven build FAILED with exit code %MVN_ERR%
    echo         Full log saved to: "%LOG%"
    echo.
    echo --------- Last 30 lines of log ---------
    call :TAIL "%LOG%" 30
    echo -----------------------------------------
    goto :END
)
echo [OK] Maven build (exit %MVN_ERR%)

REM --- 6) Locate artifacts ------------------------------------------------
echo.
echo === Step 2/2: Locate built artifacts ===
echo.

set "OUT_DIR=%~dp0target\dist-portable"
set "FOUND_PORTABLE="
if exist "%OUT_DIR%\Task Scheduler\Task Scheduler.exe" (
    echo   Portable app (always works, no Java needed on target):
    echo     - %OUT_DIR%\Task Scheduler\Task Scheduler.exe
    echo     (Zip up the whole "%OUT_DIR%\Task Scheduler" folder to share it.)
    echo.
    set "FOUND_PORTABLE=1"
) else (
    echo   (Portable APP_IMAGE not found under "%OUT_DIR%".)
)

set "OUT_DIR=%~dp0target\dist"
set "FOUND_EXE="
if exist "%OUT_DIR%" (
    for %%f in ("%OUT_DIR%\*.exe" "%OUT_DIR%\*.msi") do (
        echo   Installer (double-click to install on Windows):
        echo     - %%~ff
        echo.
        set "FOUND_EXE=1"
    )
)

if not defined FOUND_EXE (
    if defined WIX_BIN (
        echo   (No .exe or .msi installer was produced under "%OUT_DIR%", even though WiX was found.)
        echo   (Open "%LOG%" and search for jpackage lines for clues.)
    ) else (
        echo   (No .exe installer produced because WiX Toolset v3 is not installed.)
        echo    To build a real .exe installer, run:
        echo        winget install WiXToolset.wix3
        echo    then re-open this terminal (so PATH refresh picks it up) and re-run this script.
    )
)

if defined FOUND_PORTABLE (
    if not defined FOUND_EXE (
        echo.
        echo You have a working portable EXE (ready to run / send to friends).
        echo Install WiX Toolset v3 later if you also want a one-click .exe installer.
    )
)
goto :END

REM --- Helper: resolve JDK bin from a java.exe dir (handles javapath links)
:resolveJdkBinFromExe
set "CANDIDATE=%~dp1"
if "%CANDIDATE%"=="" exit /b
if exist "%CANDIDATE%\jpackage.exe" (
    if not defined JDK_BIN set "JDK_BIN=%CANDIDATE%"
    exit /b
)
for %%d in ("%CANDIDATE%\..") do (
    if exist "%%~fd\bin\jpackage.exe" (
        if not defined JDK_BIN set "JDK_BIN=%%~fd\bin"
        exit /b
    )
    if exist "%%~fd\jpackage.exe" (
        if not defined JDK_BIN set "JDK_BIN=%%~fd"
        exit /b
    )
)
exit /b

REM --- Helper: TAIL (pure batch, no PowerShell execution policy issues) ---
:TAIL
setlocal
set "FILE=%~1"
set "N=%~2"
set "COUNT=0"
for /f "tokens=*" %%L in ('type "%FILE%" 2^>nul') do set /a COUNT+=1
set "SKIP=0"
if %COUNT% GTR %N% set /a SKIP=%COUNT%-%N%
if %SKIP% GTR 0 (
    more +%SKIP% "%FILE%"
) else (
    type "%FILE%"
)
endlocal
exit /b 0

:END
echo.
echo Press any key to exit...
pause >nul
endlocal
