# vproxy

[![Build Status](https://travis-ci.com/wkgcass/vproxy.svg?branch=dev)](https://travis-ci.com/wkgcass/vproxy)

## 简介

VProxy是一个零依赖的基于NIO的TCP负载均衡器。本项目仅需要Java 11即可运行。

1) clone，2) 编译，3) 运行！

## 特性

1. TCP和TLS负载均衡
2. HTTP/1.x和HTTP/2负载均衡，支持根据Host分发请求
3. 支持其他协议的负载均衡，例如grpc, dubbo
4. Socks5服务
5. DNS服务，支持A|AAAA记录
6. 与Kubernetes整合
7. 封装好的针对特定场景的应用，例如`WebSocksProxyAgent`和`WebSocksProxyServer`

## 构建

### 打包

<details><summary>使用已构建的版本</summary>

<br>

查看 [release page](https://github.com/wkgcass/vproxy/releases).

#### For linux

使用release页面中最新的`vproxy-linux`二进制文件。

或者

使用`jlink`打包的运行时文件：点[这里](https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-5/vproxy-runtime-linux.tar.gz)下载。

#### For macos

使用release页面中最新的`vproxy-macos`二进制文件。

#### For windows

Java运行时可以从[这里](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot)下载。

#### For musl

使用`jlink`打包的运行时文件：点[这里](https://github.com/wkgcass/vproxy/releases/download/1.0.0-BETA-5/vproxy-runtime-musl.tar.gz)下载。

>注意：该运行时仍然处于beta阶段

</details>

<details><summary>打jar包</summary>

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

<details><summary>native fds impl</summary>

<br>

仅支持macos(bsd)/linux。另外在编译前，你可能需要配置`JAVA_HOME`环境变量。

```
make vfdposix
java -Dvfd=posix -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

如果要使用`F-Stack`版本，可以按照这个文档的步骤执行：[fstack-how-to.md](https://github.com/wkgcass/vproxy/blob/master/doc_zh/fstack-how-to.md)。

此外，Windows有一个特别版本用于支持Tap设备：`-Dvfd=windows`，但是普通fd和事件循环依旧是jdk selector channel.

```
make vfdwindows
java -Dvfd=posix -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

</details>

<details><summary>测试功能</summary>

执行测试用例:

```
./gradlew runTest
```

测试vswitch, docker network plugin, vpctl, k8s controller:

```shell
# 需要事先安装virtualbox

cd ./misc/auto-setup/
./auto-setup.sh
./auto-verify.sh
```

## 目标

* 零依赖: 除了java和kotlin标准库外不加任何依赖。
* 简单：代码简单易懂。
* 运行时可修改：更新配置不需要重启。
* 高效：性能是首要目标之一。
* TCP负载均衡：支持TCP以及一些基于TCP的协议，也允许你使用自己的协议。
* Kubernetes：将vproxy资源整合到k8s中。

## 如何使用

<details><summary>在K8S中使用vproxy</summary>

<br>

添加crd并启动vproxy和controller

```
kubectl apply -f https://github.com/vproxy-tools/vpctl/blob/master/misc/crd.yaml
kubectl apply -f https://github.com/vproxy-tools/vpctl/blob/master/misc/k8s-vproxy.yaml
```

启动示例应用

```
kubectl apply -f https://github.com/vproxy-tools/vpctl/blob/master/misc/cr-example.yaml
```

详细信息可见[这里](https://github.com/vproxy-tools/vpctl/blob/master/README.md)

</details>

<details><summary>vpctl</summary>

<br>

我们提供一个命令行客户端应用，来帮助你操作vproxy实例。你可以参考[vpctl的仓库](https://github.com/vproxy-tools/vpctl)以获取更多信息。

该工具经过完整的测试，并且非常简单易用。该工具的仓库里提供了一些例子供参考。

</details>

<details><summary>简易模式</summary>

<br>

你可以用一行命令启动一个简单的负载均衡:

```
java -Deploy=Simple -jar vproxy.jar \  
                bind {port} \
                backend {host1:port1,host2:port2} \
                [ssl {path of cert1,cert2} {path of key} \]
                [protocol {...} \]
```

可以输入`help`检查参数列表。

</details>

<details><summary>标准模式</summary>

<br>

使用`help`查看启动参数。

在启动vproxy实例时，会默认开启一个监听18776端口的`http-controller`和一个监听16309端口的`resp-controller`。后续则可以使用`curl`或者`redis-cli`来操作该vproxy实例。当然你也可以直接通过标准输入(stdin)来操作vproxy实例。

查看[command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md)和[api文档](https://github.com/wkgcass/vproxy/blob/master/doc/api.yaml)以获取更多信息。  
如果有任何关于实现细节的问题也欢迎在issue中提出。

</details>

### 文档

* [how-to-use.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/how-to-use.md): 如何使用配置文件和controller。
* [api.yaml](https://github.com/wkgcass/vproxy/blob/dev/doc/api.yaml): http-controller的api文档（swagger格式）。
* [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md): 详细的命令文档。
* [lb-example.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/lb-example.md): 关于TCP负载均衡的一个使用例子。
* [docker-example.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/docker-example.md): 关于构建镜像以及在docker中运行vproxy的一个例子。
* [architecture.md](https://github.com/wkgcass/vproxy/blob/master/doc/architecture.md): 架构相关。
* [extended-app.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/extended-app.md): 扩展应用的使用方式。
* [websocks.md](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md): The WebSocks Protocol.
* [vproxy-kcp-tunnel.md](https://github.com/wkgcass/vproxy/blob/master/doc/vproxy-kcp-tunnel.md): The KCP Tunnel Protocol.
* [using-application-layer-protocols.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/using-application-layer-protocols.md): 关于如何使用(自定义的)应用层协议。
* [fstack-how-to.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/fstack-how-to.md): 如何在`F-Stack`上运行vproxy。
* [vpws-direct-relay.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/vpws-direct-relay.md): 如何使用`vpws-agent`的`direct-relay`功能。

## 贡献

目前只有`我`自己在维护这个项目。希望能有更多人加入 :)

感谢[Jetbrains](https://www.jetbrains.com/?from=vproxy)制作的IDE，以及免费的开源许可证。

![](https://raw.githubusercontent.com/wkgcass/vproxy/master/doc/jetbrains.png)
