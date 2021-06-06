# 扩展应用

vproxy 不但支持传统的负载均衡、socks5服务、service mesh、服务发现，还支持一些非通用需求的应用。

## 如何使用

扩展应用在`vproxy.extended/vproxyx`以及`vproxy.app/vproxy.app.vproxyx`里，每一个应用入口都是一个`void main0(String[])`方法。

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
java -Deploy=$simple_name_of_a_class $JVM_OPTS vproxy.app.app.Main $application_args
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
* `domain`: `[可选]` 当前主机的域名。
* `redirectport`: `[可选]` 绑定一个端口并返回http 3xx，用于将客户端（浏览器）重定向到`listen`设定的端口。
* `kcp`: `[可选]` 启动kcp传输协议，监听端口和`listen`指定的端口一致。
* `webroot`: `[可选]` 网站根目录，可以用来返回一些静态页面

例如：

```
listen 80 auth alice:pasSw0rD,bob:PaSsw0Rd
listen 443 auth alice:pasSw0rD,bob:PaSsw0Rd ssl pkcs12 ~/mycertkey.p12 pkcs12pswd myPassWord domain example.com

listen 443 auth alice:pasSw0rD,bob:PaSsw0Rd ssl \
        certpem /etc/letsencrypt/live/example.com/cert.pem,/etc/letsencrypt/live/example.com/chain.pem \
        keypem /etc/letsencrypt/live/example.com/privkey.pem \
        domain example.com \
        redirectport 80 \
        kcp \
        webroot /var/www/html
```

### Deploy=WebSocksAgent

一个在本地运行的agent服务，它将websocks转换为socks5，以便被其他应用使用。

查看 [The Websocks Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks.md) 获取更多信息。

#### 启动参数

(可选) 配置文件的完整路径

如果没有指定，那么应用将使用`~/.vproxy/vpws-agent.conf`作为配置文件。

配置文件接口可以看[这里](https://github.com/wkgcass/vproxy/blob/master/doc/websocks-agent-example.conf)。

### Deploy=KcpTun

通过使用KCP加速网络。你需要在远端有一个访问目的地比较快的Server，并在本地起一个Client，在Client和Server之间建立隧道。

查看 [vproxy kcp tunnel](https://github.com/wkgcass/vproxy/blob/master/doc/vproxy-kcp-tunnel.md) 获取更多信息。

#### 启动参数

* `mode`: 运行模式。enum: client or server
* `bind`: 监听端口。对于客户端，会监听在TCP 127.0.0.1:$bind上。对于服务端，会监听在UDP 0.0.0.0:$bind上。
* `target`: 连接目标。对于客户端，填入服务端的ip:port。对于服务端，填入访问目标的ip:port。
* `fast`: 重传配置。enum: 1 or 2 or 3 or 4

例如：

```
mode client bind 50010 target 100.1.2.3:20010 fast 3
mode server bind 20010 target google.com fast 3
```
