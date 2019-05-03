# Service mesh 例子

## 例子的git仓库

[https://github.com/wkgcass/vproxy-service-mesh-example](https://github.com/wkgcass/vproxy-service-mesh-example)

## 细节

这个例子程序将创建5个容器，见`NAMES`一列：

```
CONTAINER ID        IMAGE                         COMMAND                  CREATED             STATUS              PORTS                    NAMES
74b454845559        vproxy-service-mesh-example   "/bin/bash /service-…"   4 hours ago         Up 4 hours                                   example-service-a2
8fb9da85c979        vproxy-service-mesh-example   "/bin/bash /service-…"   4 hours ago         Up 4 hours                                   example-service-b
55d019262156        vproxy-service-mesh-example   "/bin/bash /service-…"   4 hours ago         Up 4 hours                                   example-service-a
2e5000eba219        vproxy-service-mesh-example   "/bin/bash /frontend…"   4 hours ago         Up 4 hours          0.0.0.0:8080->8080/tcp   example-frontend
4a583c0804df        vproxy-service-mesh-example   "/bin/bash /lb.sh"       4 hours ago         Up 4 hours                                   example-lb
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
                  |                        +--------------+
                  |                        |              |
                  +----------------------->| SmartLBGroup |
                                           |              |
                                           +--------------+

line --------> 真实的网络流量
line --.--.--> 逻辑网络流量
```

客户端访问`Frontend`。然后`Frontend`从`Service (A 1/2)/(B)`获取数据，并返回给客户端。

```
curl localhost:8080/service-b
{"service":"b","resp":"8fb9da85c979"}

curl localhost:8080/service-a
{"service":"a","resp":"74b454845559"}

curl localhost:8080/service-a
{"service":"a","resp":"55d019262156"}
```

其中，`service`指的是服务名，`resp`内容是容器id。

当A服务和B服务启动时，它们分别向各自的sidecar注册服务。前端服务使用sidecar提供的socks5功能来代理网络流量。  
更多细节可见例子代码。

## 另一种使用方式

你可以就像例子中描述的那样来使用vproxy（lb+socks5），不过你也可以只使用lb部分，忽略socks5的部分。

LB需要你在配置文件里指定监听端口，所以你可以在你应用的配置文件里指定负载均衡的ip（或者负载均衡的虚拟ip）和端口。  
当服务启动时，这个服务需要将自身注册到sidecar中。这个服务的请求可以直接发到lb上而不是通过sidecar代理。  
就像例子中一样，新节点也可以自动被负载均衡识别到。
