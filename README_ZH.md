# vproxy

## 简介

VProxy是一个零依赖的基于NIO的TCP负载均衡器。本项目仅需要Java 11即可运行。

1) clone，2) javac，3) 运行！

此外还提供了Gradle的配置文件，请使用Gradle 4.10.2以上版本（支持java 11的gradle版本）。

## 模板

* 零依赖: 除了java标准库外不加任何依赖，也不使用jni扩展。
* 简单：代码简单易懂.
* 运行时可修改：更新配置不需要重启。
* 高效：性能是首要目标之一。
* TCP负载均衡：目前仅支持TCP。

## 如何使用

请参考如下文档。  
如果有任何关于实现细节的问题也欢迎在issue中提出。

### 文档

* [how-to-use.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/how-to-use.md): 如何使用配置文件和controller。
* [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md): 详细的命令文档。
* [lb-example.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/lb-example.md): 关于TCP负载均衡的一个使用例子。
* [service-mesh-example.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/service-mesh-example.md): 关于service mesh的一个例子。
* [architecture.md](https://github.com/wkgcass/vproxy/blob/master/doc/architecture.md): 架构相关。
* [service-mesh-protocol.md](https://github.com/wkgcass/vproxy/blob/master/doc/service-mesh-protocol.md): vproxy service mesh通信协议。
* [extended-app.md(中文)](https://github.com/wkgcass/vproxy/blob/master/doc_zh/extended-app.md): 扩展应用的使用方式。
* [websocks.md](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md): The WebSocks Protocol.

## 贡献

目前只有`我`自己在维护这个项目。希望能有更多人加入 :)
