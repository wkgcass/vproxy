# vproxy

## 简介

VProxy是一个零依赖的基于NIO的TCP负载均衡器。本项目仅需要Java 11即可运行。

1) clone，2) 编译，3) 运行！

## 构建

### 打包

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

### native fds impl

仅支持macos(bsd)/linux。另外在编译前，你可能需要配置`JAVA_HOME`环境变量。

```
cd ./src/main/c
./make-general.sh

cd ../../../
java -Dvfd=posix -Djava.library.path=./src/main/c -jar build/libs/vproxy.jar -Deploy=HelloWorld
```

如果要使用`F-Stack`版本，可以按照这个文档的步骤执行：[f-stack-how-to.md](https://github.com/wkgcass/vproxy/blob/master/doc_zh/f-stack-how-to.md)。

## 模板

* 零依赖: 除了java标准库外不加任何依赖，也不使用jni扩展。
* 简单：代码简单易懂.
* 运行时可修改：更新配置不需要重启。
* 高效：性能是首要目标之一。
* TCP负载均衡：支持TCP以及一些基于TCP的协议，也允许你使用自己的协议。

## 如何使用

### 简易模式

你可以用一行命令启动一个简单的负载均衡:

```
java -Deploy=Simple -jar vproxy.jar \  
                bind {port} \
                backend {host1:port1,host2:port2} \
                [ssl {path of cert1,cert2} {path of key} \]
                [protocol {...} \]
```

可以输入`help`检查参数列表。

### 标准模式

请参考如下文档。  
如果有任何关于实现细节的问题也欢迎在issue中提出。

### 文档

* [how-to-use.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/how-to-use.md): 如何使用配置文件和controller。
* [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md): 详细的命令文档。
* [lb-example.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/lb-example.md): 关于TCP负载均衡的一个使用例子。
* [service-mesh-example.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/service-mesh-example.md): 关于service mesh的一个例子。
* [docker-example.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/docker-example.md): 关于构建镜像以及在docker中运行vproxy的一个例子。
* [architecture.md](https://github.com/wkgcass/vproxy/blob/master/doc/architecture.md): 架构相关。
* [discovery-protocol.md](https://github.com/wkgcass/vproxy/blob/master/doc/discovery-protocol.md): vproxy自动节点发现通信协议。
* [extended-app.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/extended-app.md): 扩展应用的使用方式。
* [websocks.md](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md): The WebSocks Protocol.
* [using-application-layer-protocols.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/using-application-layer-protocols.md): 关于如何使用(自定义的)应用层协议。
* [f-stack-how-to.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/f-stack-how-to.md): 如何在`F-Stack`上运行vproxy。

## 贡献

目前只有`我`自己在维护这个项目。希望能有更多人加入 :)

感谢[Jetbrains](https://www.jetbrains.com/?from=vproxy)制作的IDE，以及免费的开源许可证。

![](https://raw.githubusercontent.com/wkgcass/vproxy/master/doc/jetbrains.png)
