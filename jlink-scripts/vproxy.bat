@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\java"
pushd %DIR% & %JAVA_EXEC%  -jar "%~dp0/../lib/vproxy.jar" %* & popd
