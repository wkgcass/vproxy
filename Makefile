SHELL := /bin/bash
.DEFAULT_GOAL := jar

VERSION := $(shell cat base/src/main/java/vproxy/base/util/Version.java | grep '_THE_VERSION_' | awk '{print $7}' | cut -d '"' -f 2)
OS := $(shell uname)

.PHONY: clean
clean:
	./gradlew clean
	rm -f ./base/src/main/c/libvfdposix.dylib
	rm -f ./base/src/main/c/libvfdposix.so
	rm -f ./base/src/main/c/libvfdfstack.so
	rm -f ./base/src/main/c/libvpxdp.so
	rm -f ./base/src/main/c/vfdwindows.dll
	cd ./base/src/main/c/xdp && make clean
	rm -f ./vproxy
	rm -f ./vproxy-*
	rm -rf ./docker-plugin-rootfs
	rm -f ./docker-plugin/vproxy
	rm -f ./docker-plugin/libvfdposix.so

.PHONY: clean-docker-plugin-rootfs
clean-docker-plugin-rootfs:
	rm -rf ./docker-plugin-rootfs

.PHONY: all
all: clean jar jlink vfdposix image docker-network-plugin

.PHONY: jar
jar:
	./gradlew jar

.PHONY: jlink
jlink: jar
	rm -rf ./build/image
	jlink --add-modules jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki --output ./build/image
	cp ./build/libs/vproxy.jar    ./build/image/lib/vproxy.jar
	cp ./jlink-scripts/vproxy     ./build/image/bin/vproxy
	cp ./jlink-scripts/vproxy.bat ./build/image/bin/vproxy.bat

.PHONY: vfdposix
vfdposix:
	cd ./base/src/main/c && ./make-general.sh

.PHONY: vpxdp
vpxdp: vfdposix
	cd ./base/src/main/c && ./make-xdp.sh

.PHONY: xdp-sample-kern
xdp-sample-kern:
	cd ./base/src/main/c/xdp && make kern

.PHONY: vfdposix-linux
ifeq ($(OS),Linux)
vfdposix-linux: vfdposix
else
vfdposix-linux:
	docker run --rm -v /Users/wkgcass/Downloads/graalvm-ce-linux:/graalvm-ce -v $(shell pwd)/base/src/main/c:/output gcc \
		bash -c -- 'cd /output && JAVA_HOME=/graalvm-ce ./make-general.sh'
endif

.PHONY: vfdwindows
vfdwindows:
	cd ./base/src/main/c && ./make-windows.sh

.PHONY: fstack
fstack:
	cd ./base/src/main/c && ./make-fstack.sh

.PHONY: image
image: jar
	native-image -jar build/libs/vproxy.jar -J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -H:ReflectionConfigurationFiles=misc/graal-reflect.json -H:JNIConfigurationFiles=misc/graal-jni.json --enable-all-security-services --no-fallback --no-server vproxy
ifeq ($(OS),Linux)
	cp vproxy vproxy-linux
endif

.PHONY: image-linux
ifeq ($(OS),Linux)
image-linux: image
	cp vproxy vproxy-linux
else
# run native-image inside a container to build linux executable file in other platforms
image-linux: jar
	docker run --rm -v /Users/wkgcass/Downloads/graalvm-ce-linux:/graalvm-ce -v $(shell pwd):/output gcc \
		/bin/bash -c "/graalvm-ce/bin/gu install native-image && /graalvm-ce/bin/native-image -jar /output/build/libs/vproxy.jar -J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -H:ReflectionConfigurationFiles=/output/misc/graal-reflect.json -H:JNIConfigurationFiles=/output/misc/graal-jni.json --enable-all-security-services --no-fallback --no-server /output/vproxy-linux"
endif

vproxy-linux:
	make image-linux

# used for releasing
.PHONY: release
ifeq ($(OS),Darwin)
release: clean jar image image-linux
	cp vproxy vproxy-macos
	cp build/libs/vproxy.jar ./vproxy-$(VERSION).jar
else
release:
	@echo "Please use macos to release"
	@exit 1
endif

.PHONY: docker-network-plugin-rootfs
docker-network-plugin-rootfs: vproxy-linux vfdposix-linux
	cp vproxy-linux ./docker-plugin/vproxy
	cp base/src/main/c/libvfdposix.so ./docker-plugin/libvfdposix.so
	docker rmi -f vproxy-rootfs:latest
	docker build -t vproxy-rootfs:latest ./docker-plugin
	mkdir -p ./docker-plugin-rootfs/rootfs
	docker create --name tmp vproxy-rootfs:latest
	docker export tmp | tar -x -C ./docker-plugin-rootfs/rootfs
	docker rm -f tmp
	docker rmi -f vproxy-rootfs:latest

docker-plugin-rootfs/rootfs:
	make docker-network-plugin-rootfs

.PHONY: docker-network-plugin
docker-network-plugin: docker-network-plugin-rootfs
	cp docker-plugin/config.json ./docker-plugin-rootfs
	docker plugin create vproxy ./docker-plugin-rootfs

.PHONY: dockertest
dockertest:
	./misc/run-tests-in-docker.sh

.PHONY: generate-command-doc
generate-command-doc: jar
	java -Deploy=GenerateCommandDoc -jar build/libs/vproxy.jar
