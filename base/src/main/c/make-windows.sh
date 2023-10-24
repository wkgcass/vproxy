#!/bin/bash

target="vfdwindows.dll"
include_platform_dir="win32"

rm -f "$target"

gcc -std=gnu99 -O2 \
    -I ./ \
    -I "../c-generated" \
    -shared -Werror -fPIC \
    io_vproxy_vfd_windows_GeneralWindows.c \
    -o "$target"
