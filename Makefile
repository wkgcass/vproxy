.DEFAULT: jar

VERSION := $(shell cat base/src/main/java/vproxybase/util/Version.java | grep '_THE_VERSION_' | awk '{print $7}' | cut -d '"' -f 2)

.PHONY: clean
clean:
	./gradlew clean
	rm -f ./base/src/main/c/libvfdposix.dylib
	rm -f ./base/src/main/c/libvfdposix.so
	rm -f ./base/src/main/c/libvfdfstack.so
	rm -f ./base/src/main/c/vfdwindows.dll
	rm -f ./vproxy
	rm -f ./vproxy-*
	rm -rf ./docker-plugin-rootfs
	rm -f ./docker-plugin/vproxy
	rm -f ./docker-plugin/libvfdposix.so

.PHONY: clean-docker-plugin-rootfs
clean-docker-plugin-rootfs:
	rm -rf ./docker-plugin-rootfs

.PHONY: jar
jar:
	./gradlew jar

.PHONY: vfdposix
vfdposix:
	cd ./base/src/main/c && ./make-general.sh

.PHONY: vfdposix-linux
vfdposix-linux:
	docker run --rm -v /Users/wkgcass/Downloads/graalvm-ce-linux:/graalvm-ce -v $(shell pwd)/base/src/main/c:/output gcc \
		bash -c -- 'cd /output && JAVA_HOME=/graalvm-ce ./make-general.sh'

.PHONY: vfdwindows
vfdwindows:
	cd ./base/src/main/c && ./make-windows.sh

.PHONY: fstack
fstack:
	cd ./base/src/main/c && ./make-fstack.sh

.PHONY: image
image: jar
	native-image -jar build/libs/vproxy.jar -H:ReflectionConfigurationFiles=misc/graal-reflect.json -H:JNIConfigurationFiles=misc/graal-jni.json --enable-all-security-services --no-fallback --no-server vproxy

# run native-image inside a container to build linux executable file in other platforms
.PHONY: image-linux
image-linux: jar
	docker run --rm -v /Users/wkgcass/Downloads/graalvm-ce-linux:/graalvm-ce -v $(shell pwd):/output gcc \
		/bin/bash -c "/graalvm-ce/bin/gu install native-image && /graalvm-ce/bin/native-image -jar /output/build/libs/vproxy.jar -H:ReflectionConfigurationFiles=/output/misc/graal-reflect.json -H:JNIConfigurationFiles=/output/misc/graal-jni.json --enable-all-security-services --no-fallback --no-server /output/vproxy-linux"

vproxy-linux:
	make image-linux

# used for releasing
.PHONY: release
release: clean jar image image-linux
	cp vproxy vproxy-macos
	cp build/libs/vproxy.jar ./vproxy-$(VERSION).jar

.PHONY: all
all: clean jar vfdposix image image-linux

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
	docker plugin create vproxy:$(VERSION) ./docker-plugin-rootfs

.PHONY: local-test-send-rootfs-to-vm
local-test-send-rootfs-to-vm: docker-plugin-rootfs/rootfs
	ssh root@100.64.0.4 docker plugin rm vproxy
	cp docker-plugin/config.json ./docker-plugin-rootfs
	tar zcf docker-plugin-rootfs.tar.gz ./docker-plugin-rootfs
	scp docker-plugin-rootfs.tar.gz root@100.64.0.4:/data
	ssh root@100.64.0.4 rm -rf /data/docker-plugin-rootfs
	ssh root@100.64.0.4 'cd /data && tar zxf docker-plugin-rootfs.tar.gz'
	ssh root@100.64.0.4 docker plugin create vproxy /data/docker-plugin-rootfs
	ssh root@100.64.0.4 docker plugin enable vproxy
