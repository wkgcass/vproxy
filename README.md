# vproxy

[中文文档](https://github.com/wkgcass/vproxy/blob/master/README_ZH.md)

## Intro

VProxy is a zero-dependency TCP Loadbalancer based on Java NIO. The project only requires Java 11 to run.

Clone it, compile it, then everything is ready for running.

## Features

1. TCP Loadbalancer with TLS termination
2. HTTP/1.x and HTTP/2 Loadbalancer with `Host` header consideration
3. Other tcp based protocol loadbalancer, such as dubbo
4. Socks5 server
5. DNS server and customizable A|AAAA records
6. Automatic service discovery
7. Many other standalone extended apps, such as `WebSocksProxyAgent` and `WebSocksProxyServer`

## Make

### directly use built releases

See the [release page](https://github.com/wkgcass/vproxy/releases).

#### For linux

Use the latest `vproxy-linux` in release page.

Or

Use the jlink built runtime [here](https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-5/vproxy-runtime-linux.tar.gz).

#### For macos

Use the latest `vproxy-macos` in release page.

#### For windows

Java runtime can be found [here](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot).

#### For musl

Use the jlink built runtime [here](https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-5/vproxy-runtime-musl.tar.gz).

>NOTE: the runtime is in beta state.

### pack

```
./gradlew clean jar
java -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

### jlink

```
./gradlew clean jlink
./build/image/bin/vproxy -Deploy=HelloWorld
```

### docker

```
docker build --no-cache -t vproxy:latest https://raw.githubusercontent.com/wkgcass/vproxy/master/docker/Dockerfile
docker run --rm vproxy -Deploy=HelloWorld
```

### graal native-image

```
./gradlew clean jar
native-image -jar build/libs/vproxy.jar --enable-all-security-services --no-fallback --no-server vproxy
./vproxy -Deploy=HelloWorld
```

### use native fds impl

Only macos(bsd)/linux supported. And you might need to set the `JAVA_HOME` env variable before compiling.

```
cd ./src/main/c
./make-general.sh

cd ../../../
java -Dvfd=posix -Djava.library.path=./src/main/c -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

For info about `F-Stack`, check the doc [f-stack-how-to.md](https://github.com/wkgcass/vproxy/blob/master/doc_zh/fstack-how-to.md).

## Aim

* Zero dependency: no dependency other than java standard library, and no jni extensions.
* Simple: keep code simple and clear.
* Modifiable when running: no need to reload for configuration update.
* Fast: performance is one of our main priorities.
* TCP Loadbalancer: we now support TCP and TCP based protocols, also allow your own protocols.
* Service mesh: provide simple service discovery mechanism and sidecar support

## How to use

### Simple mode

You can start a simple loadbalancer in one command:

```
java -Deploy=Simple -jar vproxy.jar \  
                bind {port} \
                backend {host1:port1,host2:port2} \
                [ssl {path of cert1,cert2} {path of key} \]
                [protocol {...} \]
```

Use `help` to view the parameters.

### Standard mode

See docs for help.  
Questions about implementation detail are also welcome (in issues).

### Doc

* [how-to-use.md](https://github.com/wkgcass/vproxy/blob/master/doc/how-to-use.md): How to use config file and controllers.
* [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md): Detailed command document.
* [lb-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/lb-example.md): An example about running a loadbalancer.
* [service-mesh-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/service-mesh-example.md): An example about vproxy service mesh.
* [docker-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/docker-example.md): An example about building and running vproxy in docker.
* [architecture.md](https://github.com/wkgcass/vproxy/blob/master/doc/architecture.md): Something about the architecture.
* [discovery-protocol.md](https://github.com/wkgcass/vproxy/blob/master/doc/discovery-protocol.md): The protocol which is used by vproxy auto discovery impl.
* [extended-app.md](https://github.com/wkgcass/vproxy/blob/master/doc/extended-app.md): The usage of extended applications.
* [websocks.md](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md): The WebSocks Protocol.
* [using-application-layer-protocols.md](https://github.com/wkgcass/vproxy/blob/master/doc/using-application-layer-protocols.md): About how to use (customized) application layer protocols.
* [f-stack-how-to.md](https://github.com/wkgcass/vproxy/blob/master/doc_zh/f-stack-how-to.md): How to run vproxy upon `F-Stack`. Chinese version only for now.

## Contribute

Currently only `I` myself is working on this project. I would be very happy if you want to join :)

Thanks to [Jetbrains](https://www.jetbrains.com/?from=vproxy) for their great IDEs and the free open source license.

![](https://raw.githubusercontent.com/wkgcass/vproxy/master/doc/jetbrains.png)
