# vproxy

[![Build Status](https://travis-ci.com/wkgcass/vproxy.svg?branch=dev)](https://travis-ci.com/wkgcass/vproxy)

[中文文档](https://github.com/wkgcass/vproxy/blob/master/README_ZH.md)

## Intro

VProxy is a zero-dependency TCP Loadbalancer based on Java NIO. The project only requires Java 11 to run.

Clone it, compile it, then everything is ready for running.

## Features

1. TCP Loadbalancer with TLS termination
2. HTTP/1.x and HTTP/2 Loadbalancer with `Host` header consideration
3. Other tcp based protocol loadbalancer, such as grpc, dubbo
4. Socks5 server
5. DNS server and customizable A|AAAA records
6. Kubernetes integration
7. Many other standalone extended apps, such as `WebSocksProxyAgent` and `WebSocksProxyServer`

## Make

<details><summary>use pre-built releases</summary>

<br>

See the [release page](https://github.com/wkgcass/vproxy/releases).

#### For linux

Use the latest `vproxy-linux` binary file in release page.

Or

Use the jlink built runtime [here](https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-5/vproxy-runtime-linux.tar.gz).

#### For macos

Use the latest `vproxy-macos` binary file in release page.

#### For windows

Java runtime can be found [here](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot).

#### For musl

Use the jlink built runtime [here](https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-5/vproxy-runtime-musl.tar.gz).

>NOTE: the runtime is in beta state.

</details>

<details><summary>pack</summary>

<br>

```
./gradlew clean jar
java -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

</details>

<details><summary>jlink</summary>

<br>

```
make jlink
./build/image/bin/vproxy -Deploy=HelloWorld
```

</details>

<details><summary>docker</summary>

<br>

```
docker build --no-cache -t vproxy:latest https://raw.githubusercontent.com/wkgcass/vproxy/master/docker/Dockerfile
docker run --rm vproxy -Deploy=HelloWorld
```

</details>

<details><summary>graal native-image</summary>

<br>

```
./gradlew clean jar
native-image -jar build/libs/vproxy.jar --enable-all-security-services --no-fallback --no-server vproxy
./vproxy -Deploy=HelloWorld
```

</details>

<details><summary>use native fds impl</summary>

<br>

Only macos(bsd)/linux supported. And you might need to set the `JAVA_HOME` env variable before compiling.

```
make vfdposix
java -Dvfd=posix -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

For info about `F-Stack`, check the doc [fstack-how-to.md](https://github.com/wkgcass/vproxy/blob/master/doc_zh/fstack-how-to.md).

And there's a special version for windows to support Tap devices: `-Dvfd=windows`, however the normal fds and event loop are stll based on jdk selector channel.

```
make vfdwindows
java -Dvfd=posix -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

</details>

<details><summary>test</summary>

Run test cases:

```
./gradlew runTest
```

Test vswitch, docker network plugin, vpctl, k8s controller:

```shell
# requires virtualbox installed

cd ./misc/auto-setup/
./auto-setup.sh
./auto-verify.sh
```

</details>

## Aim

* Zero dependency: no dependency other than java standard library, and no jni extensions.
* Simple: keep code simple and clear.
* Modifiable when running: no need to reload for configuration update.
* Fast: performance is one of our main priorities.
* TCP Loadbalancer: we now support TCP and TCP based protocols, also allow your own protocols.
* Kubernetes: integrate vproxy resources into k8s.

## How to use

<details><summary>use vproxy with kubernetes</summary>

<br>

Add crd, launch vproxy and controller

```
kubectl apply -f https://github.com/vproxy-tools/vpctl/blob/master/misc/crd.yaml
kubectl apply -f https://github.com/vproxy-tools/vpctl/blob/master/misc/k8s-vproxy.yaml
```

Launch the example app

```
kubectl apply -f https://github.com/vproxy-tools/vpctl/blob/master/misc/cr-example.yaml
```

Detailed info can be found [here](https://github.com/vproxy-tools/vpctl/blob/master/README.md).

</details>

<details><summary>vpctl</summary>

<br>

A command line client application is provided to manipulate the vproxy instance. You may see more info in [vpctl repo](https://github.com/vproxy-tools/vpctl).

This tool is fully tested and simple to use. Some examples are provided in the tool repo for reference.

</details>

<details><summary>Simple mode</summary>

<br>

You can start a simple loadbalancer in one command:

```
java -Deploy=Simple -jar vproxy.jar \  
                bind {port} \
                backend {host1:port1,host2:port2} \
                [ssl {path of cert1,cert2} {path of key} \]
                [protocol {...} \]
```

Use `help` to view the parameters.

</details>

<details><summary>Standard mode</summary>

<br>

Use `help` to view the launching parameters.

When launching the vproxy instance, a `http-controller` on port 18776 and a `resp-controller` on port 16309 will be started. Then you can operate the vproxy instance using `curl` or `redis-cli`. You may also operate the vproxy instance directly using standard input (stdin).

See [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md) and [api doc](https://github.com/wkgcass/vproxy/blob/master/doc/api.yaml) for more info.  
Questions about implementation detail are also welcome (in issues).

</details>

### Doc

* [how-to-use.md](https://github.com/wkgcass/vproxy/blob/master/doc/how-to-use.md): How to use config file and controllers.
* [api.yaml](https://github.com/wkgcass/vproxy/blob/dev/doc/api.yaml): api doc for http-controller in swagger format.
* [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md): Detailed command document.
* [lb-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/lb-example.md): An example about running a loadbalancer.
* [docker-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/docker-example.md): An example about building and running vproxy in docker.
* [architecture.md](https://github.com/wkgcass/vproxy/blob/master/doc/architecture.md): Something about the architecture.
* [extended-app.md](https://github.com/wkgcass/vproxy/blob/master/doc/extended-app.md): The usage of extended applications.
* [websocks.md](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md): The WebSocks Protocol.
* [vproxy-kcp-tunnel.md](https://github.com/wkgcass/vproxy/blob/master/doc/vproxy-kcp-tunnel.md): The KCP Tunnel Protocol.
* [using-application-layer-protocols.md](https://github.com/wkgcass/vproxy/blob/master/doc/using-application-layer-protocols.md): About how to use (customized) application layer protocols.
* [fstack-how-to.md](https://github.com/wkgcass/vproxy/blob/master/doc_zh/fstack-how-to.md): How to run vproxy upon `F-Stack`. Chinese version only for now.
* [vpws-direct-relay.md](https://github.com/wkgcass/vproxy/blob/master/doc_zh/vpws-direct-relay.md): How to use `direct-relay` in `vpws-agent`.

## Contribute

Currently only `I` myself is working on this project. I would be very happy if you want to join :)

Thanks to [Jetbrains](https://www.jetbrains.com/?from=vproxy) for their great IDEs and the free open source license.

![](https://raw.githubusercontent.com/wkgcass/vproxy/master/doc/jetbrains.png)
