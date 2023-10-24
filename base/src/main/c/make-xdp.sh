#!/bin/bash

cd xdp && make libbpf
cd ../

rm -f libvpxdp.so

echo "compiling libvpxdp.so ..."

gcc -std=gnu99 -O2 \
    $GCC_OPTS \
    -I ./ \
    -I "./xdp/libbpf/src" \
    -I "../c-generated" \
    -L"./xdp/libbpf/src" -Wl,--no-as-needed,--whole-archive,-lelf,-lbpf,--no-whole-archive \
    -Wl,--as-needed,--no-whole-archive \
    -shared -Werror -lc -fPIC \
    io_vproxy_xdp_NativeXDP.c ./xdp/vproxy_xdp.c ./xdp/vproxy_xdp_util.c ./xdp/vproxy_checksum.c \
    -o libvpxdp.so

echo "done"
