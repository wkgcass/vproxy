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
	rm -f ./base/src/main/c/*.dylib
	rm -f ./base/src/main/c/*.so
	rm -f ./base/src/main/c/*.dll
	cd ./base/src/main/c/xdp && make clean
	rm -f ./vproxy
	rm -f ./vproxy-*
	rm -f ./docker/vproxy.jar
	rm -rf $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs
	rm -f ./docker-plugin/vproxy.jar
	rm -f ./*.build_artifacts.txt
	rm -f ./module-info.class
	rm -rf ./io
	rm -rf ./submodules/msquic/build
	rm -f ./*.so
	rm -f ./*.dylib
	rm -f ./*.dll
	cd ./submodules/fubuki/ && cargo clean

.PHONY: clean-docker-plugin-rootfs
clean-docker-plugin-rootfs:
	rm -rf $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs

.PHONY: init
init:
	./misc/init.sh

.PHONY: all
all: clean jar-with-lib jlink image docker docker-network-plugin

.PHONY: generate-module-info
generate-module-info:
	/usr/bin/env bash ./gradlew GenerateModuleInfo

.PHONY: jar
jar: generate-module-info
	/usr/bin/env bash ./gradlew shadowJar
	zip build/libs/vproxy.jar module-info.class

.PHONY: _add_linux_so_to_zip
_add_linux_so_to_zip:
	mkdir -p ./io/vproxy/
	cp ./base/src/main/c/libvfdposix.so ./io/vproxy/libvfdposix-$(LINUX_ARCH).so
	cp ./base/src/main/c/libvpxdp.so ./io/vproxy/libvpxdp-$(LINUX_ARCH).so
	cp ./base/src/main/c/xdp/libbpf/src/libbpf.so.0.6.0 ./io/vproxy/libbpf-$(LINUX_ARCH).so
	cp ./libmsquic.so ./io/vproxy/libmsquic-$(LINUX_ARCH).so
	cp ./base/src/main/c/libmsquic-java.so ./io/vproxy/libmsquic-java-$(LINUX_ARCH).so
	cp ./submodules/fubuki/target/release/libfubukil.so ./io/vproxy/libfubuki-$(LINUX_ARCH).so
	zip build/libs/vproxy.jar \
		./io/vproxy/libvfdposix-$(LINUX_ARCH).so \
		./io/vproxy/libvpxdp-$(LINUX_ARCH).so \
		./io/vproxy/libbpf-$(LINUX_ARCH).so \
		./io/vproxy/libmsquic-$(LINUX_ARCH).so \
		./io/vproxy/libmsquic-java-$(LINUX_ARCH).so \
		./io/vproxy/libfubuki-$(LINUX_ARCH).so
	rm -r ./io

.PHONY: native
ifeq ($(OS),Linux)
native: vfdposix vpxdp quic fubuki
else ifeq ($(OS),Darwin)
native: vfdposix-linux vpxdp-linux quic-all fubuki-linux fubuki vfdposix
else
native: vfdwindows
endif

.PHONY: jar-with-lib
ifeq ($(OS),Linux)
jar-with-lib: clean jar native _add_linux_so_to_zip
else
jar-with-lib: clean jar native _add_linux_so_to_zip
	mkdir -p ./io/vproxy/
	cp ./base/src/main/c/libvfdposix.dylib ./io/vproxy/libvfdposix-$(ARCH).dylib
	cp ./libmsquic.dylib ./io/vproxy/libmsquic-$(ARCH).dylib
	cp ./base/src/main/c/libmsquic-java.dylib ./io/vproxy/libmsquic-java-$(ARCH).dylib
	cp ./submodules/fubuki/target/release/libfubukil.dylib ./io/vproxy/libfubuki-$(ARCH).dylib
	zip build/libs/vproxy.jar \
		./io/vproxy/libvfdposix-$(ARCH).dylib \
		./io/vproxy/libmsquic-$(ARCH).dylib \
		./io/vproxy/libmsquic-java-$(ARCH).dylib \
		./io/vproxy/libfubuki-$(ARCH).dylib
	rm -r ./io
endif

.PHONY: jar-no-dep
jar-no-dep: jar-with-lib
	cp build/libs/vproxy.jar build/libs/vproxy-no-dep.jar
	zip -d -q build/libs/vproxy-no-dep.jar 'org/*'
	zip -d -q build/libs/vproxy-no-dep.jar 'kotlin*'
	zip -d -q build/libs/vproxy-no-dep.jar 'DebugProbesKt.bin'
	zip -d -q build/libs/vproxy-no-dep.jar '_COROUTINE/*'
	zip -d -q build/libs/vproxy-no-dep.jar 'META-INF/kotlin*'
	zip -d -q build/libs/vproxy-no-dep.jar 'META-INF/com.android.tools/*'
	zip -d -q build/libs/vproxy-no-dep.jar 'META-INF/proguard/*'
	zip -d -q build/libs/vproxy-no-dep.jar 'META-INF/versions/*'
	zip -d -q build/libs/vproxy-no-dep.jar 'META-INF/vjson.kotlin_module'
	zip -d -q build/libs/vproxy-no-dep.jar 'io/vproxy/pni/*'
	zip -d -q build/libs/vproxy-no-dep.jar 'vjson/*'
	zip -d -q build/libs/vproxy-no-dep.jar 'vpreprocessor/*'

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
	cd ./base/src/main/c && \
	MSQUIC_LD=../../../../submodules/msquic/build/bin/Release \
	MSQUIC_INC=../../../../submodules/msquic/src/inc \
	/usr/bin/env bash ./make-quic.sh
.PHONY: msquic
msquic:
	cd ./submodules/msquic/ && make

.PHONY: fubuki
fubuki:
	cd ./submodules/fubuki/ && cargo update
	cd ./submodules/fubuki/ && cargo +nightly build --release

.PHONY: vfdposix-linux
.PHONY: vpxdp-linux
.PHONY: msquic-java-linux
.PHONY: msquic-linux
ifeq ($(OS),Linux)
vfdposix-linux: vfdposix
vpxdp-linux: vpxdp
msquic-java-linux: msquic-java
msquic-linux: msquic
fubuki-linux: fubuki
else
vfdposix-linux:
	docker run --rm -v $(shell pwd):/vproxy vproxyio/compile:latest make vfdposix
vpxdp-linux:
	docker run --rm -v $(shell pwd):/vproxy vproxyio/compile:latest make vpxdp
msquic-java-linux:
	docker run --rm -v $(shell pwd):/vproxy -v "$(shell pwd)/submodules/msquic/src/inc:/msquic/src/inc" -v "$(shell pwd)/submodules/msquic/build/bin/Release:/msquic/build/bin/Release" -e MSQUIC_INC=/msquic/src/inc -e MSQUIC_LD=/msquic/build/bin/Release vproxyio/compile:latest make msquic-java
msquic-linux:
	docker run --rm -v $(shell pwd):/vproxy vproxyio/compile:latest /bin/bash -c 'cd submodules/msquic && make'
fubuki-linux:
	docker run --rm -v $(shell pwd):/vproxy -v $(shell pwd)/cargo-cache/git:/root/.cargo/git -v $(shell pwd)/cargo-cache/registry:/root/.cargo/registry vproxyio/compile:latest /bin/bash -c 'make fubuki'
endif

.PHONY: quic
quic: vfdposix msquic msquic-java
.PHONY: quic-linux
quic-linux: vfdposix-linux msquic-linux msquic-java-linux
.PHONY: quic-all
quic-all:
	rm -rf ./submodules/msquic/build
	make quic-linux
	cp ./submodules/msquic/build/bin/Release/libmsquic.so.2.2.4 ./libmsquic.so
	rm -rf ./submodules/msquic/build
	make quic
	cp ./submodules/msquic/build/bin/Release/libmsquic.2.2.4.dylib ./libmsquic.dylib

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
