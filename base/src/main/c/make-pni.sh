#!/bin/bash

os=`uname`

target="pni"
include_platform_dir=""

if [[ "Linux" == "$os" ]]
then
	target="lib$target.so"
	include_platform_dir="linux"
elif [[ "Darwin" == "$os" ]]
then
	target="lib$target.dylib"
	include_platform_dir="darwin"
else
	target="$target.dll"
	include_platform_dir="win32"
fi

rm -f "$target"

GENERATED_PATH="../c-generated"
if [ "$VPROXY_BUILD_GRAAL_NATIVE_IMAGE" == "true" ]; then
	GENERATED_PATH="${GENERATED_PATH}-graal"
	GCC_OPTS="$GCC_OPTS -DPNI_GRAAL=1"
fi

gcc -std=gnu99 -O2 \
    $GCC_OPTS \
    -I "$GENERATED_PATH" \
    -L "$WINDIR/System32" \
    -lucrt -shared -Werror -fPIC \
    $GENERATED_PATH/pni.c \
    -o "$target"
