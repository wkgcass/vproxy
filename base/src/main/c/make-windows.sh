#!/bin/bash

target="vfdwindows.dll"
include_platform_dir="win32"

rm -f "$target"

GENERATED_PATH="../c-generated"
if [ "$VPROXY_BUILD_GRAAL_NATIVE_IMAGE" == "true" ]; then
    GENERATED_PATH="${GENERATED_PATH}-graal"
    GCC_OPTS="$GCC_OPTS -DPNI_GRAAL=1"
fi

gcc -std=gnu99 -O2 \
    -I ./ \
    -I "$GENERATED_PATH" \
    -L . \
    -lpni -shared -Werror -fPIC \
    io_vproxy_vfd_windows_GeneralWindows.c \
    -o "$target"
