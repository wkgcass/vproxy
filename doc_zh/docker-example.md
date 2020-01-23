# Docker

VProxy 提供了一个简单的 Dockerfile。

## 打镜像

```
docker build --no-cache -t vproxy:latest https://raw.githubusercontent.com/wkgcass/vproxy/master/docker/Dockerfile
```

因为Dockerfile使用完全相同的RUN语句动态地获取最新版本vproxy jar，所以打镜像时候需要使用`--no-cache`标志。

## 运行

```
docker run -d -v $AUTO_SAVE_DIR:/root vproxy:latest
```

`$AUTO_SAVE_DIR`是用来加载或存储“自动保存文件”的目录（自动保存文件名称为`.vproxy.last`）。  

如果你开启了一些端口，你可能需要使用`-p`参数暴露这些端口。或者你也可以直接使用`--net=host`选项。

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

在Dockerfile中指定了entrypoint，所以任何参数都可以正常地在`run`命令中使用。对于系统参数(System properties)你可以直接使用`-Dxxx=yyy`来指定，vproxy主函数会将这些参数处理成系统参数。
