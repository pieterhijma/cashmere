@echo off

if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT

if "%CASHMERE%X"=="X" set CASHMERE=%~dp0..

set CASHMERE_APP_ARGS=

:setupArgs
if ""%1""=="""" goto doneStart
set CASHMERE_APP_ARGS=%CASHMERE_APP_ARGS% %1
shift
goto setupArgs

:doneStart

java -classpath "%CLASSPATH%;%CASHMERE%\lib\*" -Dlog4j.configuration=file:"%CASHMERE%"\log4j.properties -Dgat.adaptor.path="%CASHMERE%"\lib\adaptors %CASHMERE_APP_ARGS%

if "%OS%"=="Windows_NT" @endlocal
