# 如何使用

在启动前可以使用`help`命令来显示可用的启动参数。  
启动后，输入`help`或者`man`可以列出指令清单。

vproxy有许多种使用方式：

* 简易模式：用一行命令启动一个简单的负载均衡
* 配置文件：在启动时或者运行中读取一个预先配置好的配置文件。
* StdIOController: 在vproxy中输入命令，并从标准输出中读取信息。
* RESPController: 使用`redis-cli`或者`telnet`来操作vproxy实例。
* Service Mesh: 让集群中的节点自动互相识别以及处理网络流量。

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

## 2. 配置文件

vproxy配置文件是一个文本文件，每一行都是一条vproxy指令。  
vproxy实例会解析每一行并一个一个的执行命令。  
这和你直接一行一行地拷贝到控制台是一个效果。

请参考文档[command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md)，查看更多命令的细节。

有三种方式来使用配置文件：

#### 2.1. 最后一次自动保存的配置文件

vproxy实例每个小时都会将当前的配置写入`~/.vproxy.last`中。  
如果使用`sigint`，`sighup`关闭或者手动关闭，配置文件也会自动保存。

如果启动vproxy时候没有带`load`参数，那么最后一次保存的配置文件会自动加载。

总体来说，你只需要配置一次，然后就不用再关心配置文件了。

#### 2.2. 启动参数

在启动时附带参数 `load ${filename}` 来加载配置文件:

例如：

```
java vproxy.app.Main load ~/vproxy.conf
```

#### 2.3. System call 指令

启动一个vproxy实例：

```
java vproxy.app.Main
```

然后输入：

```
> System call: save ~/vproxy.conf             --- 将当前配置保存到文件中
> System call: load ~/vproxy.conf             --- 从文件中读取配置
```

## 3. 使用 StdIOController

启动vproxy实例：

```
java vproxy.app.Main
```

这样，StdIOController就启动了。你可以通过stdin输入命令。

如果你经常使用StdIOController，那么推荐在tmux或者screen里启动vproxy实例。

## 4. 使用 RESPController

`RESPController` 监听一个端口，并且使用 REdis Serialization Protocol 来传输命令和结果。  
通过该Controller，你就可以使用`redis-cli`来操作vproxy实例了。

> 注意: `redis-cli` 不会将`help`命令发送到服务端，而是直接打印自己的帮助信息。  
> 注意: 所以我们提供了一个叫做`man`的命令，用来获取vproxy的帮助信息。  
> 注意: 为安全考虑，并非所有`System call:`命令都可以在RESPController中执行。  
> 注意: 你可以在启动时指定一个`allowSystemCallInNonStdIOController`标记，以便在 RESPController 中启用 system call 指令。

你可以在启动时开启RESPController，或者在StdIOController中输入命令来启动。

#### 4.1 启动参数

使用 `resp-controller ${address} ${password}` 启动参数来启动 RESPController。

例如：

```
java vproxy.app.Main resp-controller 0.0.0.0:16379 m1paSsw0rd
```

然后你就可以使用 `redis-cli` 来连入vproxy实例了。

```
redis-cli -p 16379 -a m1paSsw0rd
127.0.0.1:16379> man
```

#### 4.2. System call 命令

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

## 5. 自动节点发现

在启动时指定discovery配置文件：

```
java vproxy.app.Main discoveryConfig $path_to_config
```

如果指定了discovery配置文件，vproxy会自动搜索节点，也会将本节点中的变化通知给其他节点。  

如果指定了discovery配置文件，vproxy提供额外两种配置模块。

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

## 6. 例子和解释

### 配置文件

创建一个文件，并且输入如下文字：

```
add server-groups sgs0
add tcp-lb lb0 addr 127.0.0.1:8899 server-groups sgs0
add server-group sg0 timeout 1000 period 3000 up 4 down 5 method wrr
add server-group sg0 to server-groups sgs0 weight 10
add server s0 to server-group sg0 address 127.0.0.1:12345 weight 10
```

保存之，比如说保存到`~/vproxy.conf`。

通过 `vproxy.app.Main` 启动程序

```
java vproxy.app.Main load ~/vproxy.conf
```

一切配置都完成了。

### 使用 redis-cli

你可以通过stdIO创建一个`RESPController`，这样，你就可以用`redis-cli`客户端来执行所有命令了。

在标准输入中键入如下命令：

```
System call: add resp-controller r0 addr 0.0.0.0:16379 pass 123456
```

该命令会创建一个名为`r0`的`RESPController`实例，它监听`0.0.0.0:16379`，并且密码是`123456`。

你可以在任何可访问的节点上执行如下命令，以便操作vproxy：

```
redis-cli -p 16379 -h $vproxy主机的ip地址 -a 123456 [$你还可以直接在这里输入命令]
```

注意：`redis-cli`会自行处理`help`命令（打印redis-cli自己的帮助信息），所以我们提供了一个新命令，叫做`man`，用来获取帮助信息，内容和在stdIO中的`help`命令一样。

### 通过stdio或者redis-cli操作的详细步骤

如果要创建一个tcp负载均衡，你可以：

1. 通过 `vproxy.app.Main` 启动程序
2. 输入如下命令，或者通过redis-cli执行如下命令：
3. `add server-groups sgs0`  
    创建了一个名叫`sgs0`的ServerGroups资源。ServerGroups资源包含了多个ServerGroup（主机组）
5. `add tcp-lb lb0 addr 127.0.0.1:8899 server-groups sgs0`  
    创建了一个叫做`lb0`的tcp负载均衡。该负载均衡监听`127.0.0.1:8899`，使用`sgs0`作为它的后端服务器列表。

> 不过，推荐设置一个更大的buffer容量，例如16384。

现在你有了一个tcp负载均衡，但是它现在还不可用。  
现在lb跑起来了。你可以telnet它。但是现在还没有任何后端服务，所以连接将会马上断开。

添加一个后端，你可以这么做：

1. `add server-group sg0 timeout 1000 period 3000 up 4 down 5 method wrr`  
    这条命令创建了一个名叫`sg0`的主机组；健康检查配置为：检查超时时间为1秒，每3秒检查一次，如果有4次成功的检查则将节点视为UP，如果有5次失败则将节点视为DOWN；从组里取节点的算法为`wrr`。
2. `add server-group sg0 to server-groups sgs0 weight 10`  
    这条命令将主机组`sg0`加入了ServerGroups `sgs0`。将`sg0`加入`sgs0`是因为tcp负载均衡使用`sgs0`作为它的后端服务器列表。
3. `add server s0 to server-group sg0 address 127.0.0.1:12345 weight 10`  
    这条命令往主机组`sg0`里添加了一个名为`s0`的新主机。远端地址为`127.0.0.1:12345`，这个主机在组里的权重是`10`。

过几秒后你应当能看到一个日志，告诉你刚才刚刚加入的主机状态变为UP。在这之后，负载均衡就可以正常接收请求了。

### 解释

vproxy允许你自行支配所有的组件。  
所以配置文件可能和你想象的稍有不同。

vproxy有一个非常简单的配置语法。

```
$action $resource-type [$resource-alias] [in $resource-type $resource-alias [in ...]] [to/from $resource-$type $resource-alias] $param-key $param-value $flag
```

举个例子：

```
add server myserver0 to server-group group0 address 127.0.0.1:12345 weight 10
```

这条语句表示：我想要往主机组`group0`里添加一个一个名叫`myserver0`的主机，这个机子地址是`127.0.0.1:12345`。这台机子在这个组里的权重为`10`。

你可以通过`help`或者`man`命令来查看所有可用的资源和参数。例如:

```
> help
> man
> man tcp-lb
```

vproxy并不提供像nginx或者haproxy那样的配置文件。vproxy配置看起来更像ipvsadm。你可以控制许多底层组件，例如线程和EventLoop。并且你还可以在运行时修改所有组件，而不必重启进程。
