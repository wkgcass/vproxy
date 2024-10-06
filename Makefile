SHELL := /bin/bash
.DEFAULT_GOAL := jar

VERSION := $(shell cat base/src/main/java/io/vproxy/base/util/Version.java | grep '_THE_VERSION_' | awk '{print $7}' | cut -d '"' -f 2)
OS := $(shell uname)
ARCH := $(shell uname -m)
#
ifeq ($(ARCH),arm64)
ARCH := aarch64
else ifeq ($(ARCH),amd64)
ARCH := x86_64
endif

ifeq ($(OS),Linux)
LINUX_ARCH = $(ARCH)
else
LINUX_ARCH := $(shell docker run --rm vproxyio/compile:latest uname -m)
endif
#
ifeq ($(LINUX_ARCH),arm64)
LINUX_ARCH := aarch64
else ifeq ($(LINUX_ARCH),amd64)
LINUX_ARCH := x86_64
endif

IS_WIN = 0
ifneq (,$(findstring MINGW,$(OS)))
  IS_WIN = 1
else ifneq (,$(findstring Windows,$(OS)))
  IS_WIN = 1
endif

DOCKER_PLUGIN_WORKDIR ?= "."

.PHONY: clean-jar
clean-jar:
	/usr/bin/env bash ./gradlew clean --no-daemon

.PHONY: clean
clean: clean-jar
	rm -f ./base/src/main/c/*.dylib
	rm -f ./base/src/main/c/*.so
	rm -f ./base/src/main/c/*.dll
	cd ./submodules/vpxdp && make clean
	rm -f ./vproxy
	rm -f ./vproxy-*
	rm -f ./docker/vproxy.jar
	rm -rf $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs
	rm -f ./docker-plugin/plugin/vproxy.jar
	rm -f ./docker-plugin/proxy/vproxy.jar
	rm -f ./*.build_artifacts.txt
	rm -f ./module-info.class
	rm -rf ./io
	cd submodules/msquic && make clean
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

.PHONY: init-apt
init-apt:
	./misc/init-apt.sh

.PHONY: all
all: clean jar-with-lib jlink image docker docker-network-plugin

.PHONY: generate-module-info
generate-module-info:
	/usr/bin/env bash ./gradlew GenerateModuleInfo --no-daemon

.PHONY: jar
jar: generate-module-info
	/usr/bin/env bash ./gradlew shadowJar --no-daemon
	zip build/libs/vproxy.jar module-info.class

.PHONY: _add_linux_so_to_zip
_add_linux_so_to_zip:
	mkdir -p ./io/vproxy/
	cp ./base/src/main/c/libvfdposix.so ./io/vproxy/libvfdposix-$(LINUX_ARCH).so
	cp ./submodules/vpxdp/libvpxdp.so ./io/vproxy/libvpxdp-$(LINUX_ARCH).so
	cp ./submodules/vpxdp/submodules/xdp-tools/lib/libxdp/libxdp.so.1.4.0 ./io/vproxy/libxdp-$(LINUX_ARCH).so
	cp ./libmsquic.so ./io/vproxy/libmsquic-$(LINUX_ARCH).so
	cp ./base/src/main/c/libmsquic-java.so ./io/vproxy/libmsquic-java-$(LINUX_ARCH).so
	cp ./submodules/fubuki/target/release/libfubukil.so ./io/vproxy/libfubuki-$(LINUX_ARCH).so
	cp ./base/src/main/c/libvpfubuki.so ./io/vproxy/libvpfubuki-$(LINUX_ARCH).so
	cp ./base/src/main/c/libpni.so ./io/vproxy/libpni-$(LINUX_ARCH).so
	zip build/libs/vproxy.jar \
		./io/vproxy/libvfdposix-$(LINUX_ARCH).so \
		./io/vproxy/libvpxdp-$(LINUX_ARCH).so \
		./io/vproxy/libxdp-$(LINUX_ARCH).so \
		./io/vproxy/libmsquic-$(LINUX_ARCH).so \
		./io/vproxy/libmsquic-java-$(LINUX_ARCH).so \
		./io/vproxy/libfubuki-$(LINUX_ARCH).so \
		./io/vproxy/libvpfubuki-$(LINUX_ARCH).so \
		./io/vproxy/libpni-$(LINUX_ARCH).so
	rm -r ./io

.PHONY: native-no-docker
native-no-docker: libpni vfdposix quic fubuki
	cp ./submodules/msquic/build/bin/Release/libmsquic.2.2.4.dylib ./libmsquic.dylib
.PHONY: native
ifeq ($(OS),Linux)
native: libpni vfdposix vpxdp quic-all fubuki
else ifeq ($(OS),Darwin)
native: libpni-linux libpni vfdposix-linux vfdposix vpxdp-linux quic-all fubuki-linux fubuki
else
native: libpni vfdwindows quic fubuki
endif

.PHONY: jar-with-lib
ifeq ($(OS),Linux)
jar-with-lib: clean jar native _add_linux_so_to_zip
.PHONY: jar-with-lib-skip-native
jar-with-lib-skip-native: clean-jar jar _add_linux_so_to_zip
else
jar-with-lib: clean jar native _add_linux_so_to_zip jar-with-lib-skip-native
.PHONY: jar-with-lib-no-docker
jar-with-lib-no-docker: clean jar native-no-docker jar-with-lib-skip-native
.PHONY: jar-with-lib-skip-native
jar-with-lib-skip-native: clean-jar jar
	mkdir -p ./io/vproxy/
	cp ./base/src/main/c/libvfdposix.dylib ./io/vproxy/libvfdposix-$(ARCH).dylib
	cp ./libmsquic.dylib ./io/vproxy/libmsquic-$(ARCH).dylib
	cp ./base/src/main/c/libmsquic-java.dylib ./io/vproxy/libmsquic-java-$(ARCH).dylib
	cp ./submodules/fubuki/target/release/libfubukil.dylib ./io/vproxy/libfubuki-$(ARCH).dylib
	cp ./base/src/main/c/libvpfubuki.dylib ./io/vproxy/libvpfubuki-$(ARCH).dylib
	cp ./base/src/main/c/libpni.dylib ./io/vproxy/libpni-$(ARCH).dylib
	zip build/libs/vproxy.jar \
		./io/vproxy/libvfdposix-$(ARCH).dylib \
		./io/vproxy/libmsquic-$(ARCH).dylib \
		./io/vproxy/libmsquic-java-$(ARCH).dylib \
		./io/vproxy/libfubuki-$(ARCH).dylib \
		./io/vproxy/libvpfubuki-$(ARCH).dylib \
		./io/vproxy/libpni-$(ARCH).dylib
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
	jlink --add-modules jdk.unsupported,jdk.crypto.cryptoki --output ./build/image
	cp ./build/libs/vproxy.jar    ./build/image/lib/vproxy.jar
	cp ./jlink-scripts/vproxy     ./build/image/bin/vproxy
	cp ./jlink-scripts/vproxy.bat ./build/image/bin/vproxy.bat

.PHONY: vfdposix
vfdposix: libpni
	cd ./base/src/main/c && /usr/bin/env bash ./make-general.sh

.PHONY: vpxdp
ifeq ($(OS),Linux)
vpxdp: vfdposix
	cd ./submodules/vpxdp && make so sample_kern
else
vpxdp: vpxdp-linux
endif

.PHONY: msquic-java
ifeq (0,$(IS_WIN))
msquic-java: libpni
	cd ./base/src/main/c && \
	MSQUIC_LD=../../../../submodules/msquic/build/bin/Release \
	MSQUIC_INC=../../../../submodules/msquic/src/inc \
	/usr/bin/env bash ./make-quic.sh
else
msquic-java: libpni
	cd ./base/src/main/c && \
	MSQUIC_LD=../../../../submodules/msquic/artifacts/bin/windows/x64_Release_openssl \
	MSQUIC_INC=../../../../submodules/msquic/src/inc \
	/usr/bin/env bash ./make-quic.sh
endif
.PHONY: msquic
msquic:
	cd ./submodules/msquic/ && make

.PHONY: fubuki
fubuki: libpni
	cd ./base/src/main/c && ./make-vpfubuki.sh
	cd ./submodules/fubuki/ && cargo +nightly build --release

.PHONY: libpni-linux
.PHONY: vfdposix-linux
.PHONY: vpxdp-linux
.PHONY: msquic-java-linux
.PHONY: msquic-linux
ifeq ($(OS),Linux)
libpni-linux: libpni
vfdposix-linux: vfdposix
vpxdp-linux: vpxdp
msquic-java-linux: msquic-java
msquic-linux: msquic
fubuki-linux: fubuki
else
libpni-linux:
	docker run \
	--rm \
	-v $(shell pwd):/vproxy \
	-e VPROXY_BUILD_GRAAL_NATIVE_IMAGE="${VPROXY_BUILD_GRAAL_NATIVE_IMAGE}" \
	vproxyio/compile:latest \
	make libpni
vfdposix-linux:
	docker run \
	--rm \
	-v $(shell pwd):/vproxy \
	-e VPROXY_BUILD_GRAAL_NATIVE_IMAGE="${VPROXY_BUILD_GRAAL_NATIVE_IMAGE}" \
	vproxyio/compile:latest \
	make vfdposix
vpxdp-linux:
	docker run \
	--rm \
	-v $(shell pwd):/vproxy \
	-e VPROXY_BUILD_GRAAL_NATIVE_IMAGE="${VPROXY_BUILD_GRAAL_NATIVE_IMAGE}" \
	vproxyio/compile:latest \
	make vpxdp
msquic-java-linux:
	docker run \
	--rm \
	-v $(shell pwd):/vproxy \
	-v "$(shell pwd)/submodules/msquic/src/inc:/msquic/src/inc" \
	-v "$(shell pwd)/submodules/msquic/build/bin/Release:/msquic/build/bin/Release" \
	-e MSQUIC_INC=/msquic/src/inc \
	-e MSQUIC_LD=/msquic/build/bin/Release \
	-e VPROXY_BUILD_GRAAL_NATIVE_IMAGE="${VPROXY_BUILD_GRAAL_NATIVE_IMAGE}" \
	vproxyio/compile:latest \
	make msquic-java
msquic-linux:
	docker run \
	--rm \
	-v $(shell pwd):/vproxy \
	vproxyio/compile:latest \
	make msquic
fubuki-linux:
	docker run \
	--rm \
	-v $(shell pwd):/vproxy \
	-v $(shell pwd)/cargo-cache/git:/root/.cargo/git \
	-v $(shell pwd)/cargo-cache/registry:/root/.cargo/registry \
	vproxyio/compile:latest \
	make fubuki
endif

.PHONY: quic
ifeq (0,$(IS_WIN))
quic: vfdposix msquic msquic-java
else
quic: vfdwindows msquic msquic-java
endif
.PHONY: quic-linux
quic-linux: vfdposix-linux msquic-linux msquic-java-linux
.PHONY: _quic-all-linux
_quic-all-linux:
	rm -rf ./submodules/msquic/build
	make quic-linux
	cp ./submodules/msquic/build/bin/Release/libmsquic.so.2.2.4 ./libmsquic.so
.PHONY: quic-all
ifeq ($(OS),Linux)
quic-all: _quic-all-linux
else
quic-all: _quic-all-linux
	rm -rf ./submodules/msquic/build
	make quic
	cp ./submodules/msquic/build/bin/Release/libmsquic.2.2.4.dylib ./libmsquic.dylib
endif

.PHONY: vfdwindows
vfdwindows: libpni
	cd ./base/src/main/c && ./make-windows.sh

.PHONY: libpni
libpni:
	cd ./base/src/main/c && ./make-pni.sh

.PHONY: image
image:
	VPROXY_BUILD_GRAAL_NATIVE_IMAGE="true" make jar-with-lib
	native-image \
	-H:+UnlockExperimentalVMOptions -H:+ForeignAPISupport \
	-jar build/libs/vproxy.jar \
	-J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
	--enable-native-access=ALL-UNNAMED \
	--features=io.vproxy.base.NativeAccessGraalFeature,io.vproxy.all.GraalFeature \
	--enable-all-security-services --no-fallback \
	vproxy
ifeq ($(OS),Linux)
	cp vproxy vproxy-linux
endif

.PHONY: image-linux
ifeq ($(OS),Linux)
image-linux: image
	mv vproxy vproxy-linux
else
# run native-image inside a container to build linux executable file in other platforms
image-linux:
	VPROXY_BUILD_GRAAL_NATIVE_IMAGE="true" make jar-with-lib
	docker run \
	--rm \
	-v $(shell pwd):/workdir \
	vproxyio/graalvm-jdk-22:latest \
		native-image \
		-H:+UnlockExperimentalVMOptions -H:+ForeignAPISupport \
		-jar build/libs/vproxy.jar \
		-J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED \
		--enable-native-access=ALL-UNNAMED \
		--features=io.vproxy.base.NativeAccessGraalFeature,io.vproxy.all.GraalFeature \
		--enable-all-security-services --no-fallback \
		vproxy-linux
endif

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
	cp build/libs/vproxy.jar ./docker-plugin/proxy/vproxy.jar
	docker rmi -f vproxy-rootfs:latest
	docker build --no-cache -t vproxy-rootfs:latest ./docker-plugin/proxy
	docker create --name tmp vproxy-rootfs:latest /bin/bash
	mkdir -p $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs/rootfs
	docker export tmp | tar -x -C $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs/rootfs
	docker rm -f tmp
	docker rmi -f vproxy-rootfs:latest

$(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs/rootfs:
	make docker-network-plugin-rootfs

.PHONY: docker-network-plugin-proxy
docker-network-plugin-proxy: $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs/rootfs
	cp docker-plugin/proxy/config.json $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs
	docker plugin create vproxyio/docker-plugin $(DOCKER_PLUGIN_WORKDIR)/docker-plugin-rootfs

.PHONY: docker-network-plugin
docker-network-plugin: jar-with-lib
	cp build/libs/vproxy.jar ./docker-plugin/plugin/vproxy.jar
	docker rmi -f vproxyio/docker-network-plugin:latest
	docker build --no-cache -t vproxyio/docker-network-plugin:latest ./docker-plugin/plugin

.PHONY: dockertest
dockertest:
	./misc/run-tests-in-docker.sh

.PHONY: generate-command-doc
generate-command-doc: jar
	java -Deploy=GenerateCommandDoc -jar build/libs/vproxy.jar
