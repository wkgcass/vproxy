#!/bin/bash

error=0

if [[ -z "$MSQUIC_INC" ]]; then
  echo "You must set MSQUIC_INC properly, where the msquic include files are located"
	error=1
fi

if [[ -z "$MSQUIC_LD" ]]; then
  echo "You must set MSQUIC_LD properly, where the msquic shared library is located"
  error=1
fi

if [[ "$error" == "1" ]]; then
  exit 1
fi

os=`uname`

target="msquic-java"
include_platform_dir=""
cflags=""

if [[ "Linux" == "$os" ]]
then
	target="lib$target.so"
	include_platform_dir="linux"
	cflags="-DCX_PLATFORM_LINUX=1"
elif [[ "Darwin" == "$os" ]]
then
	target="lib$target.dylib"
	include_platform_dir="darwin"
	cflags="-DCX_PLATFORM_DARWIN=1"
else
	os="_WIN32"
	target="$target.dll"
	include_platform_dir="win32"
fi

rm -f "$target"

link="-lc -lpthread"
if [ "_WIN32" == "$os" ]
then
	link="-L $WINDIR/System32 -lucrt -lntdll"
fi

if [ "Linux" == "$os" ]
then
	NO_AS_NEEDED="-Wl,--no-as-needed"
fi
if [ "_WIN32" == "$os" ]
then
	link="$link -l:msquic.dll"
else
	link="$link -lmsquic"
fi
if [ "Linux" == "$os" ]
then
	AS_NEEDED="-Wl,--as-needed"
fi

LIBAE="../../../../submodules/libae/src"

GENERATED_DIR_NAME="c-generated"
if [ "$VPROXY_BUILD_GRAAL_NATIVE_IMAGE" == "true" ]; then
    GENERATED_DIR_NAME="${GENERATED_DIR_NAME}-graal"
    GCC_OPTS="$GCC_OPTS -DPNI_GRAAL=1"
fi

gcc -std=gnu11 -O2 \
    $GCC_OPTS \
    -I "$LIBAE" \
    -I "$MSQUIC_INC" \
    -I "../$GENERATED_DIR_NAME" \
    -I "../../../../submodules/msquic-java/core/src/main/$GENERATED_DIR_NAME" \
    -L "$MSQUIC_LD" -L . \
    -DQUIC_ENABLE_CUSTOM_EVENT_LOOP=1 \
    $cflags \
    -shared -Werror $link -fPIC \
    io_vproxy_msquic_MsQuic.c \
    ../$GENERATED_DIR_NAME/io_vproxy_msquic_CxPlatExecutionState.extra.c \
    ../$GENERATED_DIR_NAME/io_vproxy_msquic_CxPlatProcessEventLocals.extra.c \
    ../../../../submodules/msquic-java/core/src/main/c/io_vproxy_msquic_MsQuic.c \
    ../../../../submodules/msquic-java/core/src/main/c/inline.c \
    ../../../../submodules/msquic-java/core/src/main/$GENERATED_DIR_NAME/io_vproxy_msquic_MsQuicModUpcall.c \
    ../../../../submodules/msquic-java/core/src/main/$GENERATED_DIR_NAME/io_vproxy_msquic_MsQuicUpcall.c \
    ../../../../submodules/msquic-java/core/src/main/$GENERATED_DIR_NAME/io_vproxy_msquic_QuicAddr.extra.c \
    -o "$target"
