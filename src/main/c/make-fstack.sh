if [[ -z "$JAVA_HOME" ]]
then
	echo "You need to set JAVA_HOME in env"
	exit 1
fi

if [[ -z "$FF_PATH" ]]
then
	FF_PATH="/data/f-stack"
fi

rm -f libvfdfstack.so

gcc -std=gnu99 \
    -I ./dep/ae \
    -I "$JAVA_HOME/include" \
    -I "$JAVA_HOME/include/linux" \
    -I "$FF_PATH/lib" \
    -DFSTACK=1 -DHAVE_FF_KQUEUE=1 \
    -L${FF_PATH}/lib -Wl,--no-as-needed,--whole-archive,-lfstack,-ldpdk,--no-whole-archive \
    -Wl,--as-needed,--no-whole-archive -lrt -lm -ldl -lcrypto -pthread -lnuma \
    -shared -Werror -lc -fPIC \
    vfd_posix_GeneralPosix.c dep/ae/ae.c dep/ae/zmalloc.c vproxy_fstack_FStack.c \
    -o libvfdfstack.so
