.PHONY: clean jar vfdposix fstack image image-docker release all
.DEFAULT: jar

VERSION := $(shell cat src/main/java/vproxy/app/Application.java | grep '_THE_VERSION_' | awk '{print $7}' | cut -d '"' -f 2)

clean:
	./gradlew clean
	rm -f  ./src/main/c/libvfdposix.dylib
	rm -f  ./src/main/c/libvfdposix.so
	rm -f  ./src/main/c/libvfdposix.dll
	rm -f  ./src/main/c/libvfdfstack.so
	rm -f ./vproxy
	rm -f ./vproxy-*

jar:
	./gradlew jar

vfdposix:
	cd ./src/main/c && ./make-general.sh

fstack:
	cd ./src/main/c && ./make-fstack.sh

image: jar
	native-image -jar build/libs/vproxy.jar --enable-all-security-services --no-fallback --no-server vproxy

# run native-image inside a container to build linux executable file in other platforms
image-docker: jar
	docker run --rm -v /Users/wkgcass/Downloads/graalvm-ce-linux:/graalvm-ce -v $(shell pwd):/output gcc \
		/bin/bash -c "/graalvm-ce/bin/gu install native-image && /graalvm-ce/bin/native-image -jar /output/build/libs/vproxy.jar --enable-all-security-services --no-fallback --no-server /output/vproxy-linux"

# used for releasing
release: clean jar image image-docker
	cp vproxy vproxy-macos
	cp build/libs/vproxy.jar ./vproxy-$(VERSION).jar

all: clean jar vfdposix image image-docker
