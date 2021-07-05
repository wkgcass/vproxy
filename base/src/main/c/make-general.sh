#!/bin/bash

if [[ -z "$JAVA_HOME" ]]
then
	JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-11.0.2.jdk/Contents/Home"
fi

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
    -I ./dep/ae \
    -I "$JAVA_HOME/include" \
    -I "$JAVA_HOME/include/$include_platform_dir" \
    -shared -Werror -lc -fPIC \
    vproxy_vfd_posix_GeneralPosix.c dep/ae/ae.c dep/ae/zmalloc.c \
    -o "$target"
