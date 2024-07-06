#!/bin/bash

cd xdp && make libbpf
cd ../

rm -f libvpxdp.so

echo "compiling libvpxdp.so ..."

GENERATED_PATH="../c-generated"
if [ "$VPROXY_BUILD_GRAAL_NATIVE_IMAGE" == "true" ]; then
    GENERATED_PATH="${GENERATED_PATH}-graal"
    GCC_OPTS="$GCC_OPTS -DPNI_GRAAL=1"
fi

gcc -std=gnu99 -O2 \
    $GCC_OPTS \
    -I ./ \
    -I "./xdp/libbpf/src" \
    -I "$GENERATED_PATH" \
    -L . -L"./xdp/libbpf/src" -Wl,--no-as-needed,--whole-archive,-lelf,-lbpf,--no-whole-archive \
    -Wl,--as-needed,--no-whole-archive \
    -shared -Werror -lc -lpni -fPIC \
    io_vproxy_xdp_NativeXDP.c ./xdp/vproxy_xdp.c ./xdp/vproxy_xdp_util.c ./xdp/vproxy_checksum.c \
    -o libvpxdp.so

echo "done"
