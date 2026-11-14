@echo off
setlocal EnableExtensions
title CleanYourTV Launcher Installer

rem Run this file from the repository root. The launcher source lives under source\cleanyourtv-main.
pushd "%~dp0"
set "PROJECT=source\cleanyourtv-main"
set "PY=%PROJECT%\tools\venv\Scripts\python.exe"

if not exist "%PROJECT%\launcher\build-and-install.ps1" (
  echo ERROR: Launcher installer files were not found.
  echo Run this file from the updated repository folder.
  popd
  exit /b 1
)

if not "%~1"=="" set "TV_HOST=%~1"
if defined TV_HOST goto :have_tv
echo.
set /p "TV_HOST=Enter your TV IP address: "
if not defined TV_HOST (
  echo ERROR: No TV IP address entered.
  popd
  exit /b 1
)

:have_tv
echo.
echo Target TV: %TV_HOST%
echo This will build the launcher, install it over ADB, and switch the Home launcher.
echo The installer checks the new launcher before disabling the recognised stock launcher.
echo.
choice /C YN /N /M "Continue? [Y/N] "
if errorlevel 2 (
  echo Cancelled. Nothing was sent to the TV.
  popd
  exit /b 0
)

where py.exe >nul 2>&1
if not errorlevel 1 goto :make_venv_py
where python.exe >nul 2>&1
if errorlevel 1 goto :no_python

if not exist "%PY%" (
  python -m venv "%PROJECT%\tools\venv"
  if errorlevel 1 goto :venv_failed
)
goto :install_requirements

:make_venv_py
if not exist "%PY%" (
  py -3 -m venv "%PROJECT%\tools\venv"
  if errorlevel 1 goto :venv_failed
)

:install_requirements
echo.
echo Checking Python dependencies...
"%PY%" -m pip install -r "%PROJECT%\tools\requirements.txt"
if errorlevel 1 goto :pip_failed

echo.
echo Starting the original CleanYourTV install-and-rollback procedure...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%PROJECT%\launcher\build-and-install.ps1" -TvHost "%TV_HOST%"
set "RESULT=%ERRORLEVEL%"

echo.
if "%RESULT%"=="0" (
  echo SUCCESS: CleanYourTV Launcher installation completed.
) else (
  echo FAILED: Installer returned exit code %RESULT%.
  echo Read the error above. The installer is designed to leave or restore the stock launcher
  echo when installation or the Home handover check fails.
)
popd
exit /b %RESULT%

:no_python
echo ERROR: Python 3 is not installed or is not on PATH.
echo Install Python 3, then run this file again.
popd
exit /b 1

:venv_failed
echo ERROR: Could not create the Python virtual environment.
popd
exit /b 1

:pip_failed
echo ERROR: Could not install the required Python packages.
popd
exit /b 1
