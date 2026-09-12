@echo off
where py >nul 2>nul
if %errorlevel%==0 (
  py -3 "%~dp0tools\gradle_runner.py" %*
) else (
  python "%~dp0tools\gradle_runner.py" %*
)
exit /b %errorlevel%
