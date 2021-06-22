# 负载均衡的例子

这个例子使用最基础的一行一行的命令进行讲述。通过这个例子，可以直观地学习到vproxy的资源类型和配置过程。

实际使用中我们更期望使用k8s资源或者vpctl工具来配置vproxy，但是这些工具最终还是会用命令完成配置，了解了命令，再去使用这些工具会上手更快。

本文的命令的详细文档均可在[这里](https://github.com/wkgcass/vproxy/blob/master/doc/command.md)查看。

## 网络拓扑

假设我们有如下的网络拓扑图：

```
                                  +--------> BACKEND1 (:80)
                                  |          10.0.2.1
                     4c           |
 CLIENT ---------> VPROXY --------+--------> BACKEND2 (:80)
10.0.0.1   10.0.0.10 | 10.0.2.10  |          10.0.2.2
                     |            |
                 10.0.3.10        +--------> BACKEND3 (:80)
                     |                       10.0.2.3
                     |
                     *
                 10.0.3.1
                   ADMIN
```

vproxy的机子有3个ip，一个在`CLIENT`的网络下(`10.0.0.0/24`)，一个在后端实例的网络下`10.0.2.0/24`，还有一个在管理网下`10.0.3.0/24`。

此外vproxy的机子是4核的。

## 后端

```
apt-get install nginx
service nginx start
```

安装nginx，让所有后端服务开始监听`0.0.0.0:80`。

## VPROXY

### 1. 启动

启动vproxy实例的同时，为了方便管理，我们也配置一个`RESPController`。

```
java vproxy.app.app.Main resp-controller 10.0.3.10:16309 m1PasSw0rd
```

启动vproxy，并且启动了一个resp-controller，绑定了`10.0.3.10:16309`，这样`ADMIN`就可以访问它了。

### 2. 使用 redis-cli

在`ADMIN`上启动一个`redis-cli`

```
redis-cli -h 10.0.3.10 -p 16309 -a m1PasSw0rd
```

如下命令可以在`redis-cli`中执行。当然，telnet也是可以的。

### 3. 线程

该步骤可以忽略。vproxy会自动创建一个acceptor线程组和一个worker线程组。  
但是我决定还是把配置展示出来以便自行配置。

创建两个EventLoopGroup（事件循环组）：一个用来接收连接，另一个用来处理流量。

```
add event-loop-group acceptor
add event-loop-group worker
```

我们这里只在`acceptor`组内创建一个EventLoop（事件循环），但是，如果你的应用会有大量新建连接，那么你也可以在`acceptor`组内创建多个EventLoop。

此外，因为这台机器有4个核，所以我们创建3个线程来处理网络流量。

```
add event-loop acceptor1 to event-loop-group acceptor

add event-loop worker1 to event-loop-group worker
add event-loop worker2 to event-loop-group worker
add event-loop worker3 to event-loop-group worker
add event-loop worker4 to event-loop-group worker
```

### 4. 后端组

创建一个叫做`ngx`的ServerGroup(主机组)：

```
add server-group ngx timeout 500 period 1000 up 2 down 3 method wrr event-loop-group worker
```

我们使用 `worker` EventLoopGroup 来执行健康检查。

往组里添加后端：

```
add server backend1 to server-group ngx address 10.0.2.1:80 weight 10
add server backend2 to server-group ngx address 10.0.2.2:80 weight 10
add server backend3 to server-group ngx address 10.0.2.3:80 weight 10
```

你可以使用`list-detail`来查看当前的健康检查状态。

```
list-detail server in server-group ngx
```

---

创建一个`upstream`资源，并且将`ngx`组加入这个新资源里。  
`upstream`资源用于管理多个`server-group`资源。

```
add upstream backend-groups
add server-group ngx to upstream backend-groups
```

### 5. TCP 负载均衡

创建一个负载均衡器，绑定地址`10.0.0.10:80`。

```
add tcp-lb lb0 acceptor-elg acceptor event-loop-group worker address 10.0.0.10:80 upstream backend-groups in-buffer-size 16384 out-buffer-size 16384
```

这样，tcp负载均衡就启动完毕了。

### 6. 检查并保存配置

你可以在vproxy控制台中执行一些特殊的命令，这些命令不能通过`redis-cli`执行。（除非你在启动时指定`allowSystemCommandInNonStdIOController`，但是为了安全考虑，和本机文件系统或者进程相关的命令依然不可执行）。

检查配置：

```
System: list config
```

保存配置：

```
System: save ~/vproxy.conf
```

除了手动保存外，vproxy每小时都会自动向`~/.vproxy/vproxy.last`中保存配置。此外，如果程序通过`SIGINT`,`SIGHUP`停止或者手动关闭，配置文件都会自动保存。

如果在启动参数中没有出现`load`命令，那么该进程将读取最后一次保存的配置。
