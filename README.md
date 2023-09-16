# vproxy

[![Build Status](https://github.com/wkgcass/vproxy/actions/workflows/ci.yaml/badge.svg?branch=dev)](https://github.com/wkgcass/vproxy/actions/workflows/ci.yaml)

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

Use the jlink built runtime [here](https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-12/vproxy-runtime-linux.tar.gz).

#### For macos

Use the latest `vproxy-macos` binary file in release page.

#### For windows

Java runtime can be found [here](https://adoptium.net/releases.html?variant=openjdk17&jvmVariant=hotspot).

#### For musl

Use the jlink built runtime [here](https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-12/vproxy-runtime-musl.tar.gz).

</details>

<details><summary>build prerequisites</summary>

Run:  
```shell
make init
```  
to initiate submodules and some other init work.

A java agent is required as a patch for gradle.  
Copy `misc/modify-gradle-compiler-args-agent.jar` to `~/.gradle/` before compiling this project.  

</details>

<details><summary>jar package</summary>

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
# make docker
docker run -it --rm vproxyio/vproxy -Deploy=HelloWorld
```

</details>

<details><summary>graal native-image</summary>

<br>

```
make image
./vproxy -Deploy=HelloWorld
```

</details>

<details><summary>native fds impl</summary>

<br>

Only macos(bsd)/linux supported.

```
make vfdposix
java -Dvfd=posix -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

And there's a special version for windows to support Tap devices: `-Dvfd=windows`, however the normal fds and event loop are stll based on jdk selector channel.

```
make vfdwindows
java -Dvfd=windows -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

Windows TAP depends on OpenVPN TAP Driver. MacOS TAP depends on tuntaposx.

MacOS TUN, Linux TAP and TUN has no extra dependencies.

</details>

<details><summary>xdp</summary>

<br>

It's recommended to run a kernel with minimum version 5.10 (or at least 5.4) in order to use xdp support in the switch module.  
If using a lower version, you cannot share the same umem with different xdp interfaces.

To build the xdp support, you will need these packages: `apt-get install -y linux-headers-$(uname -r) build-essential libelf-dev clang llvm`, then:

```
make vpxdp
```

Or compile it inside a docker container on a non-Linux platform:

```
make vpxdp-linux
```

</details>

<details><summary>test</summary>

<br>

Run test cases:

```
./gradlew runTest
```

Run test cases in docker:

```
make dockertest
```

Test vswitch, docker network plugin, vpctl, k8s controller:

```shell
# requires virtualbox installed

cd ./misc/auto-setup/
./auto-setup.sh
./auto-verify.sh
```

</details>

<details><summary>ui</summary>

<br>

vproxy provides some ui tools.

```shell
./gradlew ui:jar
java -cp ./ui/build/libs/vproxy-ui.jar $mainClassName
```

Current available ui tools:

1. `io.vproxy.ui.calculator.CalculatorMain`: an IPv4 network calculator

</details>

## Aim

* Zero dependency: no dependency other than java and kotlin standard library.
* Simple: keep code simple and clear.
* Modifiable when running: no need to reload for configuration update.
* Fast: performance is one of our main priorities.
* TCP Loadbalancer: we now support TCP and TCP based protocols, also allow your own protocols.
* Kubernetes: integrate vproxy resources into k8s.
* SDN: modifying and forwarding packets with flows and routes.

## How to use

<details><summary>use as a library</summary>

<br>

**gradle**

```groovy
implementation group: 'io.vproxy', name: 'vproxy-adaptor-netty', version: '1.0.0-BETA-12'
// all available artifacts: dep, base, adaptor-netty, adaptor-vertx
```

**maven**

```xml
<dependency>
    <groupId>io.vproxy</groupId>
    <artifactId>vproxy-adaptor-netty</artifactId>
    <version>1.0.0-BETA-12</version>
</dependency>
<!-- all available artifacts: dep, base, adaptor-netty, adaptor-vertx -->
```

**module-info.java**

```java
requires io.vproxy.dep;
requires io.vproxy.base;
requires io.vproxy.adaptor.netty;
requires io.vproxy.adaptor.vertx;
```

**netty**

```java
var acceptelg = new VProxyEventLoopGroup();
var elg = new VProxyEventLoopGroup(4);
var bootstrap = new ServerBootstrap();
bootstrap
    .channel(VProxyInetServerSocketChannel.class)
    .childHandler(new ChannelInitializer<>() {
        @Override
        protected void initChannel(Channel ch) {
            ChannelPipeline p = ch.pipeline();
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpHelloWorldServerHandler());
        }
    });
bootstrap.group(acceptelg, elg);
bootstrap.bind(hostname, port).sync();
```

</details>

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
                [ssl {path of cert1,cert2} {path of key}] \
                [protocol {...}] \
```

Use `help` to view the parameters.

</details>

<details><summary>Standard mode</summary>

<br>

Use `help` to view the launching parameters.

After launching, you may use `help`, `man`, `man ${action}`, `man ${resource}`, `man ${resource} ${action}` to check the command manual. Also you can use `System: help` to check the system commands.

After launching vproxy, you may use `System:` to run some system commands, You may create `http-controller`s and `resp-controller`s. Then you can operate the vproxy instance using `curl` or `redis-cli`. You may also operate the vproxy instance directly using standard input (stdin).

See [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md) and [api doc](https://github.com/wkgcass/vproxy/blob/master/doc/api.yaml) for more info.

</details>

## Doc

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
* [vpws-direct-relay.md](https://github.com/wkgcass/vproxy/blob/master/doc_zh/vpws-direct-relay.md): How to use `direct-relay` in `vpws-agent`.

## Products

* [VProxy Soft Switch (vpss)](https://github.com/vproxy-tools/vpss): A soft router (switch) for your home.

## Contribute

Currently only `I` myself is working on this project. I would be very happy if you want to join :)

Thanks to those who had committed PR, see [CONTRIB](https://github.com/wkgcass/vproxy/blob/master/CONTRIB.md).
