@echo off
setlocal

REM =========================================================================
REM  Task Scheduler — One-click EXE installer builder
REM  Requires:
REM    * JDK 21+ on PATH (with jpackage tool — bundled since JDK 14)
REM    * Maven (mvn) on PATH
REM    * Optional: WiX Toolset 3.x installed (C:\Program Files (x86)\WiX Toolset v3.x\bin)
REM      -> required for "msi"; our default jpackage --type exe uses
REM         the free "Inno Setup" fallback instead. Install Inno Setup from:
REM            https://jrsoftware.org/isdl.php
REM =========================================================================

where /q java   || (echo [ERROR] java.exe not found on PATH. Install JDK 21+ first. & exit /b 1)
where /q mvn    || (echo [ERROR] mvn.exe not found on PATH. Install Maven first.    & exit /b 1)
where /q jpackage || (echo [ERROR] jpackage not found. JDK 14+ with jpackage is required. & exit /b 1)

echo.
echo === Step 1/2: mvn clean package (compiles + runs jpackage) ===
echo.
call mvn clean package -DskipTests
set MVN_ERR=%ERRORLEVEL%
if %MVN_ERR% NEQ 0 (
    echo.
    echo [ERROR] mvn package failed with exit code %MVN_ERR%.
    echo Check mvn output above. Common causes:
    echo   - JAVA_HOME / jpackage / module-info mismatches
    echo   - WiX or Inno Setup not installed for --type EXE
    exit /b %MVN_ERR%
)

echo.
echo === Step 2/2: Locate installer ===
echo.
set "OUT_DIR=%~dp0target\dist"
if exist "%OUT_DIR%" (
    dir /b "%OUT_DIR%\*.exe" 2>nul
    echo.
    echo Installer(s) located in: "%OUT_DIR%"
) else (
    echo [WARN] No output directory at "%OUT_DIR%". Did the jpackage plugin run?
)

echo.
echo Done.
endlocal
