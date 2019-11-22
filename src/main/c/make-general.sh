if [ -z "$JAVA_HOME" ]
then
	JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-11.0.2.jdk/Contents/Home"
fi

os=`uname`

target="vfdposix"

if [ "Linux" == "$os" ]
then
	target="lib$target.so"
elif [ "Darwin" == "$os" ]
then
	target="lib$target.dylib"
else
	echo "unsupported platform $os"
	exit 1
fi

gcc -I ./dep/ae -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/darwin" -shared -static -Werror -lc vfd_posix_GeneralPosix.c dep/ae/ae.c dep/ae/zmalloc.c -o libvfdposix.dylib
