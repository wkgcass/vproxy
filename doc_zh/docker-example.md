# Docker

VProxy 提供了一个简单的 Dockerfile。

## 打镜像

```
docker build --no-cache -t vproxy:latest https://raw.githubusercontent.com/wkgcass/vproxy/master/docker/Dockerfile
```

因为Dockerfile使用完全相同的RUN语句动态地获取最新版本vproxy jar，所以打镜像时候需要使用`--no-cache`标志。

## 运行

```
docker run -d -v $AUTO_SAVE_DIR:/root -p 16379:16379 vproxy:latest
```

`$AUTO_SAVE_DIR`是用来加载或存储“自动保存文件”的目录（自动保存文件名称为`.vproxy.last`）。  
`16379`是Dockerfile里定义的resp-controller的端口，打开这个端口以便使用`redis-cli`来操作它。

如果你有更多的端口，你同样需要打开那些端口。或者你也可以直接使用`--net=host`选项。

## 检查

```
docker logs $CONTAINER_ID -f
```

## 退出

使用SIGTERM来保存当前配置并退出

```
docker stop $CONTAINER_ID
```

## 自定义启动参数

* 使用`java`指代java可执行文件。
* 使用`/vproxy.jar`或者`vproxy.jar`指代vproxy jar包。
* 此外，如果你需要在容器里使用家目录，记得转义`"~"`。

例子：

```
docker run -p 1080:1080 --read-only -v /path-to-config-dir:/root vproxy:latest java -Deploy=WebSocksProxyAgent -jar vproxy.jar "~/vproxy-websocks-agent.conf"
```
