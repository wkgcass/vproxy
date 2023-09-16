#!/bin/bash

os=`uname`

target="vfdposix"
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
	echo "unsupported platform $os"
	exit 1
fi

rm -f "$target"

gcc -std=gnu99 -O2 \
    $GCC_OPTS \
    -I ./dep/ae \
    -I "../../../../dep/src/main/c" \
    -I "../c-generated" \
    -shared -Werror -lc -lpthread -fPIC \
    io_vproxy_vfd_posix_GeneralPosix.c dep/ae/ae.c dep/ae/zmalloc.c \
    -o "$target"
