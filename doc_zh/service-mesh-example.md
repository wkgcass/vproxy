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
```

这几个容器组成了如下的一个网络：

```
                                                                          +-----------------+
                                                                          |                 |
                                   +------------------------------------->|   Service A 1   |
                                   |                                      |                 |
           +--------------+        |                                      +-----------------+
           |              |        |                                      |     sidecar     |
client---->|   Frontend   |        |                                      +-----------------+
           |              |        |
           +--------------+        |
           |    sidecar   |------->+
           +--------------+        |       +---------------+
                                   |       |               |
                                   +------>|   Service B   |
                                   |       |               |
                                   |       +---------------+
                                   |       |    sidecar    |
                                   |       +---------------+              +-----------------+
                                   |                                      |                 |
                                   +------------------------------------->|   Service A 2   |
                                                                          |                 |
                                                                          +-----------------+
                                                                          |     sidecar     |
                                                                          +-----------------+
```

客户端访问`Frontend`。然后`Frontend`从`Service (A 1/2)/(B)`获取数据，并返回给客户端。

```
curl localhost:8080/service-b
{"service":"b","host":"8fb9da85c979","port":17729,"weight":10}

curl localhost:8080/service-a
{"service":"a","host":"74b454845559","port":28168,"weight":15}

curl localhost:8080/service-a
{"service":"a","host":"55d019262156","port":29315,"weight":10}
```

* `service`指的是服务名
* `host`内容是容器id
* `port`是由sidecar分配的本地监听端口
* `weight`是节点的权重

当A服务和B服务启动时，它们分别向各自的sidecar注册服务。前端服务使用sidecar提供的socks5功能来代理网络流量。  
更多细节可见例子代码。

## 另一种使用方式

你也可以在sidecar上使用负载均衡而非socks5。如果你使用负载均衡，那么你需要直接请求127.0.0.1上暴露的负载均衡端口。
