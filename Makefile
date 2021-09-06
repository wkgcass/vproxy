SHELL := /bin/bash
.DEFAULT_GOAL := jar

VERSION := $(shell cat base/src/main/java/vproxy/base/util/Version.java | grep '_THE_VERSION_' | awk '{print $7}' | cut -d '"' -f 2)
OS := $(shell uname)
ARCH := $(shell uname -m)
ifeq ($(OS),Linux)
LINUX_ARCH = $(ARCH)
else
LINUX_ARCH = $(shell docker run --rm wkgcass/vproxy-compile:latest uname -m)
endif
DOCKER_PLUGIN_WORKDIR ?= "."

.PHONY: clean-jar
clean-jar:
	/usr/bin/env bash ./gradlew clean

.PHONY: clean
clean: clean-jar
	rm -f ./base/src/main/c/libvfdposix.dylib
	rm -f ./base/src/main/c/libvfdposix.so
	rm -f ./base/src/main/c/libvfdfstack.so
	rm -f ./base/src/main/c/libvpxdp.so
	rm -f ./base/src/main/c/vfdwindows.dll
	cd ./base/src/main/c/xdp && make clean
	rm -f ./vproxy
	rm -f ./vproxy-*
	rm -rf $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs
	rm -f ./docker-plugin/vproxy.jar
	rm -f ./*.build_artifacts.txt
	rm -f ./module-info.class

.PHONY: clean-docker-plugin-rootfs
clean-docker-plugin-rootfs:
	rm -rf $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs

.PHONY: all
all: clean jar jlink vfdposix image docker-network-plugin

.PHONY: generate-module-info
generate-module-info:
	/usr/bin/env bash ./gradlew GenerateModuleInfo

.PHONY: jar
jar: generate-module-info
	/usr/bin/env bash ./gradlew jar
	zip build/libs/vproxy.jar module-info.class

.PHONY: _add_linux_so_to_zip
_add_linux_so_to_zip:
	cp ./base/src/main/c/libvfdposix.so ./libvfdposix-$(LINUX_ARCH).so
	cp ./base/src/main/c/libvpxdp.so ./libvpxdp-$(LINUX_ARCH).so
	cp base/src/main/c/xdp/libbpf/src/libbpf.so.0.4.0 ./libbpf-$(LINUX_ARCH).so
	zip build/libs/vproxy.jar ./libvfdposix-$(LINUX_ARCH).so ./libvpxdp-$(LINUX_ARCH).so ./libbpf-$(LINUX_ARCH).so
	rm ./libvfdposix-$(LINUX_ARCH).so ./libvpxdp-$(LINUX_ARCH).so ./libbpf-$(LINUX_ARCH).so

.PHONY: jar-with-lib
ifeq ($(OS),Linux)
jar-with-lib: jar vfdposix vpxdp _add_linux_so_to_zip
else
jar-with-lib: jar vfdposix-linux vpxdp-linux vfdposix _add_linux_so_to_zip
	cp ./base/src/main/c/libvfdposix.dylib ./libvfdposix-$(ARCH).dylib
	zip build/libs/vproxy.jar ./libvfdposix-$(ARCH).dylib
	rm ./libvfdposix-$(ARCH).dylib
endif

.PHONY: jar-no-kt-runtime
jar-no-kt-runtime: jar-with-lib
	cp build/libs/vproxy.jar build/libs/vproxy-no-kt-runtime.jar
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'org/intellij/*'
	zip -d -q build/libs/vproxy-no-kt-runtime.jar 'org/jetbrains/*'
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

.PHONY: vfdposix-linux
.PHONY: vpxdp-linux
ifeq ($(OS),Linux)
vfdposix-linux: vfdposix
vpxdp-linux: vpxdp
else
vfdposix-linux:
	docker run --rm -v $(shell pwd):/vproxy wkgcass/vproxy-compile:latest make vfdposix
vpxdp-linux:
	docker run --rm -v $(shell pwd):/vproxy wkgcass/vproxy-compile:latest make vpxdp
endif

.PHONY: vfdwindows
vfdwindows:
	cd ./base/src/main/c && ./make-windows.sh

.PHONY: fstack
fstack:
	cd ./base/src/main/c && /usr/bin/env bash ./make-fstack.sh

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
	docker run --rm -v $(shell pwd):/vproxy wkgcass/vproxy-compile:latest \
		native-image -jar build/libs/vproxy.jar -J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED -H:ReflectionConfigurationFiles=misc/graal-reflect.json -H:JNIConfigurationFiles=misc/graal-jni.json --enable-all-security-services --no-fallback --no-server vproxy-linux
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
	docker plugin create wkgcass/vproxy-docker-plugin $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs

.PHONY: dockertest
dockertest:
	./misc/run-tests-in-docker.sh

.PHONY: generate-command-doc
generate-command-doc: jar
	java -Deploy=GenerateCommandDoc -jar build/libs/vproxy.jar
