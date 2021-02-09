@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\java"
pushd %DIR% & %JAVA_EXEC% --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -jar "%~dp0/../lib/vproxy.jar" %* & popd
