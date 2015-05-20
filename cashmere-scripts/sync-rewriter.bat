@echo off

if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT

set IBISC_ARGS=

:setupArgs
if ""%1""=="""" goto doneStart
set IBISC_ARGS=%IBISC_ARGS% "%1"
shift
goto setupArgs

:doneStart

java -classpath "%CLASSPATH%;%CASHMERE%\lib\*" -Dlog4j.configuration=file:"%CASHMERE%"\log4j.properties ibis.cashmere.impl.syncrewriter.SyncRewriter %IBISC_ARGS%

if "%OS%"=="Windows_NT" @endlocal

