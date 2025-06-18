@echo off
setlocal enabledelayedexpansion

set "script_dir=%~dp0"

call :get_property "groovy.version" groovy_version
call :get_property "groovy.downloadUrlPrefix" download_url_prefix
set "groovy_dir=%script_dir%\groovy-%groovy_version%"

if not exist "%groovy_dir%" (
    echo Downloading Groovy %groovy_version%...
    set "download_url=%download_url_prefix%-%groovy_version%.zip"
    curl -L "!download_url!" -o "%script_dir%\groovy.zip"
    echo Unpacking Groovy %groovy_version%...
    powershell -Command "Expand-Archive -Path '%script_dir%\groovy.zip' -DestinationPath '%script_dir%' -Force"
    del "%script_dir%\groovy.zip"
)

"%groovy_dir%\bin\groovy.bat" %*
goto :eof

:get_property
set "property_name=%~1"
for /f "tokens=2 delims==" %%a in ('findstr "%property_name%" "%script_dir%\properties.toml"') do (
    set "temp_value=%%a"
    set "temp_value=!temp_value: =!"
    set "temp_value=!temp_value:"=!"
    set "%~2=!temp_value!"
)
goto :eof
