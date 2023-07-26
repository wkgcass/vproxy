#!/bin/bash

if [[ -z "$JAVA_HOME" ]]
then
    echo "You need to define JAVA_HOME env first"
    exit 1
fi

target="vfdwindows.dll"
include_platform_dir="win32"

rm -f "$target"

gcc -std=gnu99 -O2 \
    -I ./ \
    -I "$JAVA_HOME/include" \
    -I "$JAVA_HOME/include/$include_platform_dir" \
    -I "../../../../dep/src/main/c" \
    -I "../c-generated" \
    -shared -Werror -fPIC \
    io_vproxy_vfd_windows_GeneralWindows.c \
    -o "$target"
