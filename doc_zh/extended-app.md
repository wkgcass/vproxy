# 扩展应用

vproxy 不但支持传统的负载均衡、socks5服务、service mesh、服务发现，还支持一些特定领域使用的应用。

## 如何使用

扩展应用在包`net.cassite.vproxyx`下，每一个应用入口都是一个`void main0(String[])`方法。

```
java -D+A:AppClass=$simple_name_of_a_class $JVM_OPTS -jar $the_jar_of_vproxy $application_args
# 或者
java -D+A:AppClass=$simple_name_of_a_class $JVM_OPTS net.cassite.vproxy.app.Main $application_args
```

例如：

```
java -D+A:AppClass=WebSocks5ProxyServer -jar vproxy.jar listen 18686 auth alice:pasSw0rD,bob:PaSsw0Rd
```

## 可用的应用

### AppClass=WebSocks5ProxyServer

一个代理服务器，即使在websocket网关后面也可以代理裸tcp流量。

查看 [The Websocks5 Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks5.md) 获取更多信息。

#### 启动参数

* `listen`: 一个整数，用于表示服务器应当监听哪个端口。
* `auth`: 一个`用户名:密码`对的序列，用`,`分割。

例如：

```
listen 18686 auth alice:pasSw0rD,bob:PaSsw0Rd
```

### AppClass=WebSocks5Agent

一个在本地运行的agent服务，它将websocks5转换为socks5，以便被其他应用使用。

查看 [The Websocks5 Protocol](https://github.com/wkgcass/vproxy/blob/master/doc/websocks5.md) 获取更多信息。

#### 启动参数

(可选) 配置文件的完整路径

如果没有指定，那么应用将使用`~/vproxy-websocks5-agent.conf`作为配置文件。

配置文件接口可以看[这里](https://github.com/wkgcass/vproxy/blob/master/src/test/resources/websocks5-agent-example.conf)。
