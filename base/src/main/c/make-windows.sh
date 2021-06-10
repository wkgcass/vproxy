#!/bin/bash

if [[ -z "$JAVA_HOME" ]]
then
    echo "You need to define JAVA_HOME env first"
    exit 1
fi

target="vfdwindows.dll"
include_platform_dir="win32"

rm -f "$target"

gcc -std=gnu99 \
    -I "$JAVA_HOME/include" \
    -I "$JAVA_HOME/include/$include_platform_dir" \
    -shared -Werror -fPIC \
    vproxy_vfd_windows_GeneralWindows.c \
    -o "$target"
