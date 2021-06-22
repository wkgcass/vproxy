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
7. [Kubernetes](#k8s): 在k8s集群中使用vproxy。

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

使用一行命令应用所有配置：

```
vpctl apply -f my.cnf.yaml
```

使用`get`命令查看配置:

e.g.

```
vpctl get TcpLb
```

```
vpctl get DnsServer dns0 -o yaml
```

<div id="config"></div>

## 3. 配置文件

vproxy配置文件是一个文本文件，每一行都是一条vproxy指令。  
vproxy实例会一行一行地解析并执行命令。  
这和你直接在控制台敲命令是一个效果。

请参考文档[command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md)，查看更多命令的细节。

有三种方式来使用配置文件：

#### 3.1. 最后一次自动保存的配置文件

vproxy实例每个小时都会将当前的配置写入`~/.vproxy.last`中。  
如果使用`sigint`，`sighup`关闭或者手动关闭，配置文件也会自动保存。

vproxy启动时会自动加载最后一次保存的配置文件。

#### 3.2. 启动参数

在启动时附带参数 `load ${filename}` 来加载配置文件:

例如：

```
java vproxy.app.app.Main load ~/vproxy.conf
```

> 可以同时指定多个配置文件，每个配置文件都会被读取

#### 3.3. 系统指令

启动一个vproxy实例：

```
java vproxy.app.app.Main
```

然后输入：

```
> System: save ~/vproxy.conf             --- 将当前配置保存到文件中
> System: load ~/vproxy.conf             --- 从文件中读取配置
```

> 你可以使用`noLoadLast`禁止启动时的配置文件读取。  
> 你可以使用`noSave`禁止配置文件保存（无论是手动保存还是自动保存都会被禁止）。

<div id="stdio"></div>

## 4. 使用 StdIOController

启动vproxy实例：

```
java vproxy.app.app.Main
```

此时，StdIOController就已经默认启动了。你可以直接在控制台输入命令。

如果你经常使用StdIOController，那么推荐在`tmux`或者`screen`里启动vproxy实例。

> 你可以使用`noStdIOController`来关闭StdIOController。

<div id="resp"></div>

## 5. 使用 RESPController

`RESPController` 监听一个端口，并且使用 REdis Serialization Protocol 来传输命令和结果。  
通过该Controller，你就可以使用`redis-cli`来操作vproxy实例了。

```
redis-cli -p 16309 -a m1paSsw0rd
127.0.0.1:16309> man
```

> 注意: `redis-cli` 不会将`help`命令发送到服务端，而是直接打印自己的帮助信息。  
> 注意: 所以我们提供了一个叫做`man`的命令，用来获取vproxy的帮助信息。  
> 注意: 为安全考虑，并非所有`System:`命令都可以在RESPController中执行。  
> 注意: 你可以在启动时指定一个`allowSystemCommandInNonStdIOController`标记，以便在 RESPController 中启用系统指令。

在启动vproxy时，`resp-controller`就会默认自动启动，监听`16309`，使用密码`123456`。  
你也可以使用启动参数或者在StdIOController中使用命令控制RESPController。

#### 5.1 启动参数

使用 `resp-controller ${address} ${password}` 启动参数来启动 RESPController。

例如：

```
java vproxy.app.app.Main resp-controller 0.0.0.0:16309 m1paSsw0rd
```

#### 5.2. 通过系统指令

启动vproxy实例：

```
java vproxy.app.app.Main
```

你可以输入如下命令来启动RESPController：

```
> System: add resp-controller ${name} address ${host:port} password ${pass}
```

你可以输入如下命令来查看已有的RESPController：

```
> System: list-detail resp-controller
resp-controller	127.0.0.1:16309              ---- 返回内容
>
```

你可以输入如下命令来停止一个RESPController：

```
> System: remove resp-controller ${name}
(done)                                       ---- 返回内容
>
```

<div id="http"></div>

## 6. 使用 HTTPController

`HTTPController`监听一个端口，并且提供http restful api来方便你控制该vproxy实例。

```
curl http://127.0.0.1:18776/api/v1/module/tcp-lb
curl http://127.0.0.1:18776/healthz
```

#### 6.1. 启动参数

使用 `http-controller ${address}`来启动HTTPController

e.g.

```
java vproxy.app.app.Main http-controller 0.0.0.0:18776
```

#### 6.2. 通过系统指令

你可以输入如下命令来创建一个HTTPController

```
> System: add http-controller ${name} address ${host:port}
```

输入如下命令查看当前的HTTPController

```
> System: list-detail http-controller
http-controller        0.0.0.0:18776              ---- this is response
```

输入如下命令停止HTTPController

```
> System: remove http-controller ${name}
(done)                                       ---- this is response
```

#### 6.3. api doc

查看swagger 2.0格式的[api文档](https://github.com/wkgcass/vproxy/blob/master/doc/api.yaml)。

<div id="k8s"></div>

## 7. Kubernetes

使用Service和vproxy CRD来实现网关、Sidecar等功能。

请转到[vpctl](https://github.com/vproxy-tools/vpctl)查看更详细的内容。
