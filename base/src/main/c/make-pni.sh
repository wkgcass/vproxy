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
	os="_WIN32"
	target="$target.dll"
	include_platform_dir="win32"
fi

rm -f "$target"

GENERATED_PATH="../c-generated"
if [ "$VPROXY_BUILD_GRAAL_NATIVE_IMAGE" == "true" ]; then
	GENERATED_PATH="${GENERATED_PATH}-graal"
	GCC_OPTS="$GCC_OPTS -DPNI_GRAAL=1"
fi

inc=""
link="-lc"
if [ "_WIN32" == "$os" ]
then
	inc="-L $WINDIR/System32"
	link="-lucrt"
fi

gcc -std=gnu99 -O2 \
    $GCC_OPTS \
    -I "$GENERATED_PATH" \
    $inc \
    $link -shared -Werror -fPIC \
    $GENERATED_PATH/pni.c \
    -o "$target"
