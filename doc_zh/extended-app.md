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

### Deploy=HelloWorld

启动一个http server和一个udp echo server。并且启动client来访问它们，以检查环境和基础功能是否正常。

### Deploy=Simple

vproxy简易负载均衡。你可以用一行shell命令启动一个完整的负载均衡。

查看 [how-to-use](https://github.com/wkgcass/vproxy/blob/master/doc/how-to-use.md) 获取更多信息。

### Deploy=Daemon

专为`Systemd`设计的一个daemon进程，用于启动、reload、自动拉起vproxy进程。

#### 启动参数

所有接受的启动参数均被用于启动子进程（vproxy进程）。

### Deploy=WebSocksProxyServer

一个代理服务器，即使在websocket网关后面也可以代理裸tcp流量。

查看 [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) 获取更多信息。

#### 启动参数

* `listen`: 一个整数，用于表示服务器应当监听哪个端口。
* `auth`: 一个`用户名:密码`对的序列，用`,`分割。
* `ssl`: 一个标志位，用于表示使用TLS连接。当该选项启用时，(`pkcs12`和`pkcs12pswd`)或者(`certpem`和`keypem`)均需要指定。pkcs和pem二选一，不能同时指定。
* `pkcs12`: pkcs12文件的路径，文件中需要包含证书和私钥。
* `pkcs12pswd`: pkcs12文件的密码。
* `certpem`: 证书文件路径. 多个证书文件可以用`,`分割。
* `keypem`: 私钥文件路径。
* `domain`: 当前主机的域名（可选）。
* `redirectport`: 绑定一个端口并返回http 3xx，用于将客户端（浏览器）重定向到`listen`设定的端口。
* `kcp`: 启动kcp传输协议，监听端口和`listen`指定的端口一致。

例如：

```
listen 80 auth alice:pasSw0rD,bob:PaSsw0Rd
listen 443 auth alice:pasSw0rD,bob:PaSsw0Rd ssl pkcs12 ~/mycertkey.p12 pkcs12pswd myPassWord domain example.com

listen 443 auth alice:pasSw0rD,bob:PaSsw0Rd ssl \
        certpem /etc/letsencrypt/live/example.com/cert.pem,/etc/letsencrypt/live/example.com/chain.pem \
        keypem /etc/letsencrypt/live/example.com/privkey.pem \
        domain example.com \
        redirectport 80 \
        kcp
```

### Deploy=WebSocksAgent

一个在本地运行的agent服务，它将websocks转换为socks5，以便被其他应用使用。

查看 [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) 获取更多信息。

#### 启动参数

(可选) 配置文件的完整路径

如果没有指定，那么应用将使用`~/vproxy-websocks-agent.conf`作为配置文件。

配置文件接口可以看[这里](https://github.com/wkgcass/vproxy/blob/master/src/test/resources/websocks-agent-example.conf)。
