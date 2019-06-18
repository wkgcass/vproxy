# 扩展应用

vproxy 不但支持传统的负载均衡、socks5服务、service mesh、服务发现，还支持一些非通用需求的应用。

## 如何使用

扩展应用在包`vproxyx`下，每一个应用入口都是一个`void main0(String[])`方法。

使用`系统属性 -D`来指定要运行的应用所在的类。

注意：这是由`-D`指定的系统属性，而不是程序参数。

```
-Deploy=$simple_name_of_a_class
```

```shell
java -Deploy=$simple_name_of_a_class $JVM_OPTS -jar $the_jar_of_vproxy $application_args
#
# 或者
#
java -Deploy=$simple_name_of_a_class $JVM_OPTS vproxy.app.Main $application_args
```

例如：

```
java -Deploy=WebSocksProxyServer -jar vproxy.jar listen 18686 auth alice:pasSw0rD,bob:PaSsw0Rd
```

## 可用的应用

### Deploy=WebSocksProxyServer

一个代理服务器，即使在websocket网关后面也可以代理裸tcp流量。

查看 [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) 获取更多信息。

#### 启动参数

* `listen`: 一个整数，用于表示服务器应当监听哪个端口。
* `auth`: 一个`用户名:密码`对的序列，用`,`分割。
* `ssl`: 一个标志位，用于表示使用TLS连接。当该选项启用时，`pkcs12`和`pkcs12pswd`均需要指定。
* `pkcs12`: pkcs12文件的路径，文件中需要包含证书和私钥。
* `pkcs12pswd`: pkcs12文件的密码。
* `domain`: 当前主机的域名（可选）。

例如：

```
listen 80 auth alice:pasSw0rD,bob:PaSsw0Rd
listen 443 auth alice:pasSw0rD,bob:PaSsw0Rd ssl pkcs12 ~/mycertkey.p12 pkcs12pswd myPassWord domain example.com
```

### Deploy=WebSocksAgent

一个在本地运行的agent服务，它将websocks转换为socks5，以便被其他应用使用。

查看 [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) 获取更多信息。

#### 启动参数

(可选) 配置文件的完整路径

如果没有指定，那么应用将使用`~/vproxy-websocks-agent.conf`作为配置文件。

配置文件接口可以看[这里](https://github.com/wkgcass/vproxy/blob/master/src/test/resources/websocks-agent-example.conf)。
