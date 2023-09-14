SHELL := /bin/bash
.DEFAULT_GOAL := jar

VERSION := $(shell cat base/src/main/java/io/vproxy/base/util/Version.java | grep '_THE_VERSION_' | awk '{print $7}' | cut -d '"' -f 2)
OS := $(shell uname)
ARCH := $(shell uname -m)
ifeq ($(OS),Linux)
LINUX_ARCH = $(ARCH)
else
LINUX_ARCH = $(shell docker run --rm vproxyio/compile:latest uname -m)
endif
DOCKER_PLUGIN_WORKDIR ?= "."

.PHONY: clean-jar
clean-jar:
	/usr/bin/env bash ./gradlew clean

.PHONY: clean
clean: clean-jar
	rm -f ./base/src/main/c/libvfdposix.dylib
	rm -f ./base/src/main/c/libvfdposix.so
	rm -f ./base/src/main/c/libvpxdp.so
	rm -f ./base/src/main/c/vfdwindows.dll
	rm -f ./base/src/main/c/libvpquic.dylib
	rm -f ./base/src/main/c/libvpquic.so
	cd ./base/src/main/c/xdp && make clean
	rm -f ./vproxy
	rm -f ./vproxy-*
	rm -f ./docker/vproxy.jar
	rm -rf $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs
	rm -f ./docker-plugin/vproxy.jar
	rm -f ./*.build_artifacts.txt
	rm -f ./module-info.class
	rm -rf ./io
	rm -f ./submodules/msquic-java/core/src/main/c/libmsquic-java.dylib
	rm -f ./submodules/msquic-java/core/src/main/c/libmsquic-java.so
	rm -f ./submodules/msquic-java/core/src/main/c/msquic-java.dll
	rm -rf ./submodules/msquic/build

.PHONY: clean-docker-plugin-rootfs
clean-docker-plugin-rootfs:
	rm -rf $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs

.PHONY: init
init:
	git submodule update --init --recursive
	cd submodules/panama-native-interface && ./gradlew clean shadowJar
	cd submodules/ && git clone https://github.com/wkgcass/msquic --branch=modified --depth=1 || exit 0
	cd submodules/msquic && git submodule update --init --recursive --recommend-shallow

.PHONY: all
all: clean jar-with-lib jlink vfdposix image docker docker-network-plugin

.PHONY: generate-module-info
generate-module-info:
	/usr/bin/env bash ./gradlew GenerateModuleInfo

.PHONY: jar
jar: generate-module-info
	/usr/bin/env bash ./gradlew jar
	zip build/libs/vproxy.jar module-info.class

.PHONY: _add_linux_so_to_zip
_add_linux_so_to_zip:
	mkdir -p ./io/vproxy/
	cp ./base/src/main/c/libvfdposix.so ./io/vproxy/libvfdposix-$(LINUX_ARCH).so
	cp ./base/src/main/c/libvpxdp.so ./io/vproxy/libvpxdp-$(LINUX_ARCH).so
	cp ./base/src/main/c/xdp/libbpf/src/libbpf.so.0.6.0 ./io/vproxy/libbpf-$(LINUX_ARCH).so
	zip build/libs/vproxy.jar ./io/vproxy/libvfdposix-$(LINUX_ARCH).so ./io/vproxy/libvpxdp-$(LINUX_ARCH).so ./io/vproxy/libbpf-$(LINUX_ARCH).so
	rm -r ./io

.PHONY: jar-with-lib
ifeq ($(OS),Linux)
jar-with-lib: jar vfdposix vpxdp _add_linux_so_to_zip
else
jar-with-lib: jar vfdposix-linux vpxdp-linux vfdposix _add_linux_so_to_zip
	mkdir -p ./io/vproxy/
	cp ./base/src/main/c/libvfdposix.dylib ./io/vproxy/libvfdposix-$(ARCH).dylib
	zip build/libs/vproxy.jar ./io/vproxy/libvfdposix-$(ARCH).dylib
	rm -r ./io
endif

.PHONY: jar-no-kt-runtime
jar-no-kt-runtime: jar-with-lib
	cp build/libs/vproxy.jar build/libs/vproxy-no-kt-runtime.jar
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'org/*'
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'kotlin*'
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'DebugProbesKt.bin'
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'META-INF/kotlin*'
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'META-INF/maven/*'
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'META-INF/proguard/*'
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'META-INF/versions/*'

.PHONY: jlink
jlink: jar
	rm -rf ./build/image
	jlink --add-modules jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki --output ./build/image
	cp ./build/libs/vproxy.jar    ./build/image/lib/vproxy.jar
	cp ./jlink-scripts/vproxy     ./build/image/bin/vproxy
	cp ./jlink-scripts/vproxy.bat ./build/image/bin/vproxy.bat

.PHONY: vfdposix
vfdposix:
	cd ./base/src/main/c && /usr/bin/env bash ./make-general.sh

.PHONY: vpxdp
vpxdp: vfdposix
	cd ./base/src/main/c && /usr/bin/env bash ./make-xdp.sh

.PHONY: xdp-sample-kern
xdp-sample-kern:
	cd ./base/src/main/c/xdp && make kern

.PHONY: msquic-java
msquic-java:
	cd ./submodules/msquic-java/core/src/main/c && \
	MSQUIC_LD=../../../../../msquic/build/bin/Release \
	MSQUIC_INC=../../../../../msquic/src/inc \
	/usr/bin/env bash ./make-quic.sh
.PHONY: vpquic
vpquic:
	cd ./base/src/main/c && \
	MSQUIC_LD=../../../../submodules/msquic/build/bin/Release \
	MSQUIC_INC=../../../../submodules/msquic/src/inc \
	/usr/bin/env bash ./make-quic.sh
.PHONY: msquic
msquic:
	cd ./submodules/msquic/ && make

.PHONY: vfdposix-linux
.PHONY: vpxdp-linux
.PHONY: msquic-java-linux
.PHONY: vpquic-linux
.PHONY: msquic-linux
ifeq ($(OS),Linux)
vfdposix-linux: vfdposix
vpxdp-linux: vpxdp
vpquic-linux: vpquic
msquic-linux: msquic
else
vfdposix-linux:
	docker run --rm -v $(shell pwd):/vproxy vproxyio/compile:latest make vfdposix
vpxdp-linux:
	docker run --rm -v $(shell pwd):/vproxy vproxyio/compile:latest make vpxdp
msquic-java-linux:
	docker run --rm -v $(shell pwd):/vproxy -v "$(shell pwd)/submodules/msquic/src/inc:/msquic/src/inc" -v "$(shell pwd)/submodules/msquic/build/bin/Release:/msquic/build/bin/Release" -e MSQUIC_INC=/msquic/src/inc -e MSQUIC_LD=/msquic/build/bin/Release vproxyio/compile:latest make msquic-java
vpquic-linux:
	docker run --rm -v $(shell pwd):/vproxy -v "$(shell pwd)/submodules/msquic/src/inc:/msquic/src/inc" -v "$(shell pwd)/submodules/msquic/build/bin/Release:/msquic/build/bin/Release" -e MSQUIC_INC=/msquic/src/inc -e MSQUIC_LD=/msquic/build/bin/Release vproxyio/compile:latest make vpquic
msquic-linux:
	docker run --rm -v $(shell pwd)/submodules/msquic:/msquic vproxyio/msquic-compile:latest make
endif

.PHONY: quic
quic: vfdposix msquic msquic-java vpquic
.PHONY: quic-linux
quic-linux: vfdposix-linux msquic-linux msquic-java-linux vpquic-linux

.PHONY: vfdwindows
vfdwindows:
	cd ./base/src/main/c && ./make-windows.sh

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
	docker run --rm -v $(shell pwd):/vproxy vproxyio/compile:latest \
		native-image -jar build/libs/vproxy.jar -J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -H:ReflectionConfigurationFiles=misc/graal-reflect.json -H:JNIConfigurationFiles=misc/graal-jni.json --enable-all-security-services --no-fallback --no-server vproxy-linux
endif

vproxy-linux:
	make image-linux

# used for releasing
.PHONY: release
ifeq ($(OS),Darwin)
release: clean jar-with-lib image image-linux docker docker-network-plugin
	cp vproxy vproxy-macos
	cp build/libs/vproxy.jar ./vproxy-$(VERSION).jar
else
release:
	@echo "Please use macos to release"
	@exit 1
endif

.PHONY: docker
docker: jar-with-lib
	cp build/libs/vproxy.jar ./docker/vproxy.jar
	docker rmi -f vproxyio/vproxy:latest
	docker build --no-cache -t vproxyio/vproxy:latest ./docker

.PHONY: docker-network-plugin-rootfs
docker-network-plugin-rootfs: jar-with-lib
	cp build/libs/vproxy.jar ./docker-plugin/vproxy.jar
	docker rmi -f vproxy-rootfs:latest
	docker build --no-cache -t vproxy-rootfs:latest ./docker-plugin
	docker create --name tmp vproxy-rootfs:latest /bin/bash
	mkdir -p $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs/rootfs
	docker export tmp | tar -x -C $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs/rootfs
	docker rm -f tmp
	docker rmi -f vproxy-rootfs:latest

$(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs/rootfs:
	make docker-network-plugin-rootfs

.PHONY: docker-network-plugin
docker-network-plugin: $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs/rootfs
	cp docker-plugin/config.json $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs
	docker plugin create vproxyio/docker-plugin $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs

.PHONY: dockertest
dockertest:
	./misc/run-tests-in-docker.sh

.PHONY: generate-command-doc
generate-command-doc: jar
	java -Deploy=GenerateCommandDoc -jar build/libs/vproxy.jar
