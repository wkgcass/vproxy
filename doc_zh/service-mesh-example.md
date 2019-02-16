# Service mesh 例子

## 例子的git仓库

[https://github.com/wkgcass/vproxy-service-mesh-example](https://github.com/wkgcass/vproxy-service-mesh-example)

## 细节

这个例子程序将创建5个容器，见`NAMES`一列：

```
CONTAINER ID        IMAGE                         COMMAND                  CREATED             STATUS              PORTS                    NAMES
8679ffb59411        vproxy-service-mesh-example   "/bin/bash /service-…"   6 seconds ago       Up 6 seconds                                 example-service-a2
7c6138d1b123        vproxy-service-mesh-example   "/bin/bash /service-…"   7 seconds ago       Up 7 seconds                                 example-service-b
908131c78cba        vproxy-service-mesh-example   "/bin/bash /service-…"   8 seconds ago       Up 7 seconds                                 example-service-a
27b420698fe0        vproxy-service-mesh-example   "/bin/bash /frontend…"   9 seconds ago       Up 8 seconds        0.0.0.0:8080->8080/tcp   example-frontend
d7f70ba0fefb        vproxy-service-mesh-example   "/bin/bash /autolb.sh"   9 seconds ago       Up 9 seconds                                 example-auto-lb
```

这几个容器组成了如下的一个网络：

```
                                                                          +-----------------+
                                                                          |                 |
                                   +-.---.---.---.---.---.---.---.---.--->|   Service A 1   |
                                   |                                      |                 |
                                   |                                      +-----------------+
                                   .                                      |     sidecar     |
           +--------------+        |                                      +-----------------+
           |              |        .                                              ^
client---->|   Frontend   |-.--.---+                                              |
           |              |        ,                                              |
           +--------------+        |       +---------------+                      |
           |    sidecar   |        |       |               |                      +------------------+
           +--------------+        +.--.-->|   Service B   |                                         |
                  |                .       |               |                                         |
                  |                |       +---------------+                                         |
                  |                |       |    sidecar    |              +-----------------+        |
                  |                |       +---------------+              |                 |        |
                  |                .              ^                       |   Service A 2   |        |
                  |                +--.---.---.---|---.---.---.---.---.-->|                 |        |
                  |                               |                       +-----------------+        |
                  |                               |                       |     sidecar     |        |
                  |                               |                       +-----------------+        |
                  |                               |                                ^                 |
                  |                               |                                |                 |
                  |                               |                                |                 |
                  |                               +--------------------------------+-----------------+
                  |                               |
                  |                               |
                  |                               |
                  |                        +---------------+
                  |                        |               |
                  +----------------------->|    AutoLB     |
                                           |               |
                                           +---------------+

line --------> 真实的网络流量
line --.--.--> 逻辑网络流量
```

客户端访问`Frontend`。然后`Frontend`从`Service (A 1/2)/(B)`获取数据，并返回给客户端。

```
curl localhost:8080/service-b
{"service":"b","resp":"7c6138d1b123"}

curl localhost:8080/service-a
{"service":"a","resp":"908131c78cba"}

curl localhost:8080/service-a
{"service":"a","resp":"8679ffb59411"}
```

其中，`service`指的是服务名，`resp`内容是容器id。

当A服务和B服务启动时，它们分别向各自的sidecar注册服务。前端服务使用sidecar提供的socks5功能来代理网络流量。  
更多细节可见例子代码。

## 另一种使用方式

你可以就像例子中描述的那样来使用vproxy（lb+socks5），不过你也可以只使用lb部分，忽略socks5的部分。

Auto-lb配置文件需要你为每一个服务指定一个监听端口，所以你可以在你应用的配置文件里指定负载均衡的ip（或者负载均衡的虚拟ip）和端口。  
当服务启动时，这个服务需要将自身注册到sidecar中。这个服务的请求可以直接发到lb上而不是通过sidecar代理。  
就像例子中一样，新节点也可以自动被负载均衡识别到。
