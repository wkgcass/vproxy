# 如何使用

在启动前可以使用`help`命令来显示可用的启动参数。  
启动后，输入`help`或者`man`可以列出指令清单。

vproxy有许多种使用方式：

1. [简易模式](#simple)：用一行命令启动一个简单的负载均衡。
2. [<font color="green">**vpctl** (recommended)</font>](#vpctl): 一个完全独立的命令行应用，用于控制vproxy实例。
3. [配置文件](#config)：在启动时或者运行中读取一个预先配置好的配置文件。
4. [StdIOController](#stdio): 在vproxy中输入命令，并从标准输出中读取信息。
5. [RESPController](#resp): 使用`redis-cli`或者`telnet`来操作vproxy实例。
6. [HTTPController](#http): 使用`curl`等http工具操作vproxy实例。
7. [Service Mesh](#discovery): 让集群中的节点自动互相识别以及处理网络流量。

<div id="simple"></div>

## 1. 简易模式

You can start a simple loadbalancer in one command:
你可以用一行命令启动一个简单的负载均衡：

例如：

```
java -Deploy=Simple -jar vproxy.jar \
                bind 8888 \
                backend 127.0.0.1:80,127.0.0.1:8080 \
                ssl ~/cert.pem ~/rsa.pem \
                protocol http
```

负载均衡监听`8888`端口，使用http(s)协议，TLS证书使用`~/cert.pem`，私钥使用`~/rsa.pem`，流量分别转发到`127.0.0.1:80`和`127.0.0.1:8080`。

你可以使用`gen`来生成与参数相符的配置文件，详情可见`配置文件`一节。

<div id="vpctl"></div>

## <font color="green">2. vpctl</font>

在[这里](https://github.com/vproxy-tools/vpctl)获取`vpctl`的代码和二进制文件。配置文件示例也在该仓库中存放。

在使用`vpctl`之前需要开启http-controller。vpctl使用http接口来控制vproxy实例。

你可以使用启动参数开启http-controller。

```
java -jar vproxy.jar http-controller 127.0.0.1:18776
```

或者也可以参考[HTTPController](#http)一节的文档内容。

<div id="config"></div>

## 3. 配置文件

vproxy配置文件是一个文本文件，每一行都是一条vproxy指令。  
vproxy实例会解析每一行并一个一个的执行命令。  
这和你直接一行一行地拷贝到控制台是一个效果。

请参考文档[command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md)，查看更多命令的细节。

有三种方式来使用配置文件：

#### 3.1. 最后一次自动保存的配置文件

vproxy实例每个小时都会将当前的配置写入`~/.vproxy.last`中。  
如果使用`sigint`，`sighup`关闭或者手动关闭，配置文件也会自动保存。

如果启动vproxy时候没有带`load`参数，那么最后一次保存的配置文件会自动加载。

总体来说，你只需要配置一次，然后就不用再关心配置文件了。

#### 3.2. 启动参数

在启动时附带参数 `load ${filename}` 来加载配置文件:

例如：

```
java vproxy.app.Main load ~/vproxy.conf
```

#### 3.3. System call 指令

启动一个vproxy实例：

```
java vproxy.app.Main
```

然后输入：

```
> System call: save ~/vproxy.conf             --- 将当前配置保存到文件中
> System call: load ~/vproxy.conf             --- 从文件中读取配置
```

<div id="stdio"></div>

## 4. 使用 StdIOController

启动vproxy实例：

```
java vproxy.app.Main
```

这样，StdIOController就启动了。你可以通过stdin输入命令。

如果你经常使用StdIOController，那么推荐在tmux或者screen里启动vproxy实例。

<div id="resp"></div>

## 5. 使用 RESPController

`RESPController` 监听一个端口，并且使用 REdis Serialization Protocol 来传输命令和结果。  
通过该Controller，你就可以使用`redis-cli`来操作vproxy实例了。

```
redis-cli -p 16379 -a m1paSsw0rd
127.0.0.1:16379> man
```

> 注意: `redis-cli` 不会将`help`命令发送到服务端，而是直接打印自己的帮助信息。  
> 注意: 所以我们提供了一个叫做`man`的命令，用来获取vproxy的帮助信息。  
> 注意: 为安全考虑，并非所有`System call:`命令都可以在RESPController中执行。  
> 注意: 你可以在启动时指定一个`allowSystemCallInNonStdIOController`标记，以便在 RESPController 中启用 system call 指令。

你可以在启动时开启RESPController，或者在StdIOController中输入命令来启动。

#### 5.1 启动参数

使用 `resp-controller ${address} ${password}` 启动参数来启动 RESPController。

例如：

```
java vproxy.app.Main resp-controller 0.0.0.0:16379 m1paSsw0rd
```

#### 5.2. System call 命令

启动vproxy实例：

```
java vproxy.app.Main
```

你可以输入如下命令来启动RESPController：

```
> System call: add resp-controller ${name} address ${host:port} password ${pass}
```

你可以输入如下命令来查看已有的RESPController：

```
> System call: list-detail resp-controller
resp-controller	127.0.0.1:16379              ---- 返回内容
>
```

你可以输入如下命令来停止一个RESPController：

```
> System call: remove resp-controller ${name}
(done)                                       ---- 返回内容
>
```

<div id="http"></div>

## 6. 使用 HTTPController

`HTTPController`监听一个端口，并且提供http restful api来方便你控制该vproxy实例。

```
curl http://127.0.0.1:18776/api/v1/module/tcp-lb
```

#### 6.1. 启动参数

使用 `http-controller ${address}`来启动HTTPController

e.g.

```
java vproxy.app.Main http-controller 0.0.0.0:18776
```

#### 6.2. System Call 命令

你可以输入如下命令来创建一个HTTPController

```
> System call: add http-controller ${name} address ${host:port}
```

输入如下命令查看当前的HTTPController

```
> System call: list-detail http-controller
http-controller        0.0.0.0:18776              ---- this is response
```

输入如下命令停止HTTPController

```
> System call: remove http-controller ${name}
(done)                                       ---- this is response
```

#### 6.3. api doc

查看swagger 2.0格式的[api文档](https://github.com/wkgcass/vproxy/blob/master/doc/api.yaml)。

<div id="discovery"></div>

## 7. 自动节点发现

在启动时指定discovery配置文件：

```
java vproxy.app.Main [discoveryConfig $path_to_config]
```

如果没有指定discovery配置文件，vproxy将会初始化一组默认配置。  
如果（除了loopback网卡之外）只有一张网卡，那么默认配置就足以正常工作了。否则你可能需要手动进行配置。  

vproxy提供两种与"自动节点发现"相关的配置模块。

* smart-group-delegate: 监控节点变化，并更新托管的server-group资源
* smart-node-delegate: 向discovery网络中注册一个服务，并通知其他节点

用户app可以用http客户端来操作vproxy配置

例如：你可以使用http请求注册或者移除一个服务：

```
POST /api/v1/module/smart-node-delegate
{
  "name": "my-test-service",
  "service": "my-service,
  "zone": "test",
  "nic": "eth0",
  "exposedPort": 8080
}
成功时返回204

DELETE /api/v1/module/smart-node-delegate/my-test-service
成功时返回204
```

你也可以检查注册在当前vproxy实例上的服务列表：

```
GET /api/v1/module/smart-node-delegate
```

关于本节内容，可以参考[service-mesh-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/service-mesh-example.md)的示例代码。  
此外，例子中还提供了一个帮助你注册节点的工具类。
