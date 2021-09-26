# 如何搭建 vproxy WebSocksProxyAgent direct-relay

本篇文章介绍了如何搭建最完整的vproxy WebSocksProxyAgent的direct-relay功能。在搭建成功后，客户端机器只需配置DNS解析，即可对**任意**使用域名访问网络服务的程序提供代理转发。

vpws-agent的配置可以在[这里](https://vproxy-tools.github.io/vpwsui/index.html)生成。

## 功能介绍

1. vproxy提供了一个代理方案，叫做WebSocks，简称vpws（**VP**roxy **W**eb**S**ocks）。
2. vpws提供一个agent，用来监听各种代理协议，例如socks5、http-connect、ss。并提供PAC、DNS功能。agent会将流量转发到vpws server。agent和server有各种功能细节配置，提供各种场景下的代理解决方案。
3. vpws agent提供一个功能，叫做`direct-relay`。该功能原理是通过DNS将“需要代理的域名”解析到agent，并且agent监听相应的ip地址。当客户端访问这个ip地址的时候，代理转发到远端。由于客户端直接访问agent而非通过socks5等带额外header的协议，所以这里取名为`direct`-relay。
4. 这个功能分为两块，基础版支持任何操作系统，但是仅对tls协议+443端口生效（http+80端口会自动返回3xx重定向到https+443）。
5. 高级版仅支持Linux，可以支持任何协议任何端口。非linux操作系统仅需VirtualBox即可运行。

本篇文章介绍direct-relay基础版和高级版的工作原理、配置细节。

## 概念

vpws网络流如下所示：

```
CLIENT <---> AGENT ---> FIREWALL ---> SERVER ---> TARGET
  |            |                                    ^
  |            |                                    |
  |            +------------------------------------+
  |                                                 |
  +-------------------------------------------------+
```

* CLIENT：客户端，流量的发起者。例如浏览器、APP等。
* AGENT：vpws agent，代理的一部分，运行于CLIENT一侧的网络环境下。
* FIREWALL：防火墙
* SERVER：vpws server，代理的一部分，运行于TARGET一侧的网络环境下。
* TARGET：CLIENT要访问的目标。

## 基础版

### 工作原理

当开启direct-relay时，agent会监听`0.0.0.0:80`和`0.0.0.0:443`，在80口，仅接受http请求，并且永远返回3xx，将请求重定向至https+443端口。例如请求`http://baidu.com`，会重定向至`https://baidu.com`。这个策略适配于所有访问网站时的浏览器行为。

DNS将需要代理的域名解析到agent上。例如解析`baidu.com`到`127.0.0.1`。

在浏览器中输入`baidu.com`回车后，首先浏览器会进行DNS解析，并获取agent地址。

然后浏览器会对agent发起http请求，`GET http://baidu.com:80`，此时浏览器回看到agent返回的重定向response，此时浏览器将请求变为`GET https://baidu.com:443`再次请求agent。

agent收到443口的请求后，会按照TLS的`CLIENT_HELLO`对第一个包做解析，获取其中的SNI字段。SNI字段会记录请求的域名。在这里就是`baidu.com`。

有了域名信息，agent就能运行代理了。代理通道建立后，请求会被原封不动的发往远端服务器、并从远端原封不动的返回回来。中途不会进行任何TLS处理，所以客户端并不需要配置任何证书信任。

```
CLIENT --------1. dns req-------> AGENT                     SERVER                  TARGET
       <-------2. dns resp-------
       --------3. http req------>
       <-----4. http resp 3xx----
       ----------5. tls--------->
                                        ----6. proxy tcp--->
                                                                    -----7. tcp---->
                                                                    <----8. tcp-----
                                        <---9. proxy tcp----
       <---------10. tcp---------
```

### 前置准备

1. 你需要搭建好并正常运行vpws server以及vpws agent，至少能够通过socks5成功进行代理转发。
2. 准备好配置文件，待会我们要修改它。
3. 保证DNS解析功能处于打开状态：`agent.dns.listen 53`。
4. 客户端机的DNS配置为agent的地址，比方说如果agent运行在本地，则配置127.0.0.1。

### 配置方式

在配置文件中打开`direct-relay`

```
agent.direct-relay on
```

由于监听53、80和443端口，所以启动agent时，可能需要root/管理员权限。Windows下使用管理员启动cmd，然后启动agent。MacOs和Linux下加上sudo前缀，并启动agent。

### 期望结果

1. 在客户端机上任意浏览器中（不打开任何代理选项），直接访问要代理的域名。
2. 在agent日志中能看到DNS解析和代理。

## 高级版

### 工作原理

和基础版本类似，高级版的agent工作时，也是通过DNS将请求解析到agent，并对流入的流量做转发。但是相比基础版仅监听`0.0.0.0:80/443`，高级版本会同时“监听”**整个**网段所有ip和所有端口号，比如`100.64.0.0/10`，有`4194304`个ip \* `65535`个端口。

显然，我们不可能采用通常的思路绑定这些ip和端口。首先在网卡上配置`2^22`个ip就相当不现实，更别说应用里面开启`274873712640`个监听socket了。

但是，在Linux中，我们可以通过简单的hack做到这一点。不需修改linux源码也不需要乱七八糟的第三方软件，仅使用linux自带的路由和tproxy功能即可实现。

详细原理分析见这里：[https://blog.cloudflare.com/how-we-built-spectrum/](https://blog.cloudflare.com/how-we-built-spectrum/)。

通过配置，我们只监听一个`IP_TRANSPARENT`端口，就可以接收整个网段\*任意端口的流量。

agent在处理DNS解析请求时，从指定网段中分配一个地址，并记录分配的ip地址到域名映射：（ip -> domain），然后返回这个ip给客户端。

客户端将会请求它拿到的ip。这时，agent检查收到的连接的目的ip，并从映射中找到对应的域名，执行代理。

这种方式对于请求没有任何限制，即使不是http、tls，即使端口号不是80、443，也可以正常进行代理。并且无需做任何解析，仅通过连接元数据就可以代理，适用范围非常广。

```
CLIENT --------1. dns req-------> AGENT                     SERVER                  TARGET
                           2. bind ip->domain
       <-------3. dns resp-------
       ----------4. tcp--------->
                                        ----5. proxy tcp--->
                                                                    -----6. tcp---->
                                                                    <----7. tcp-----
                                        <---8. proxy tcp----
       <---------9. tcp----------
```

### 前置准备

1. 你需要搭建好并正常运行vpws server以及vpws agent，至少能够通过socks5成功进行代理转发。
2. 准备好配置文件，待会我们要修改它。
3. 下载并安装好VirtualBox。在官网下载安装即可。
4. 准备一个Linux ISO系统安装镜像，直接使用最新版Ubuntu Server即可。不要使用Desktop版，太浪费（除非你一机多用）。
5. 知道如何安装Linux，本文不会介绍在虚拟机中安装系统。按提示操作很简单完全不需要单独学习。

### 虚拟机操作步骤

1. 创建一个虚拟机，大部分配置都无关紧要，主要是网卡配置
2. 在`VirtualBox`选项栏中选择`管理` -> `主机网络管理器`。
3. `创建`一个虚拟网卡。配置“IPv4”地址为`100.64.0.1`，配置“IPv4网络掩码”为`255.192.0.0`。点击“应用”。
4. 切换到“DHCP服务器”选项卡，勾选“启用服务器”，这时服务器地址和掩码都应当会自动配好，可以参考这两个值`100.64.0.2`，`255.192.0.0`。
5. 最小地址配置`100.64.0.4`，最大地址配置`100.95.255.255`。配置成这样是因为，我们会给我们这台虚拟机配置一个静态ip`100.64.0.3`，并且我们要分配给“假ip”的网段是`100.96.0.0/11`。剩下的ip可以留给今后其他可能创建的虚拟机，启动DHCP是为了简化操作系统首次安装时的网络配置，理论上一切都可以自动配好。
6. 保存选项后来到虚拟机的网卡选项。
7. 打开1号网卡，选择“网络地址转换NAT”。使用这个选项是为了方便虚拟机访问外部网络。在任何情况下，只要宿主机能够访问外网，那么虚拟机就也能够访问。如果你对网络足够有把握，这个网卡也可以配置为其他的模式。
8. 暂时只使用这一个网卡，启动虚拟机，完成操作系统的安装。建议安装时语言选择`en-US`，最后询问是否安装软件时，用空格勾选SSH Server，后续做操作时可以使用ssh（windows下可以使用putty等软件）登录进行操作（当然直接在虚拟机屏幕里操作也可以）。
9. 关闭虚拟机，到网卡设置里，打开2号网卡。选择“仅主机(Host-Only)网络”，界面名称选择刚才创建的虚拟网卡。
10. 启动并来到虚拟机中。按下面规则配置网卡：1. eth0（1号网卡）为dhcp（默认应当已经配好）。2. eth1（2号网卡）为静态ip（主要是方便后续管理）。配置完成后重启。

    ```
    vim /etc/network/interfaces
    ```

    ```
    auto lo
    iface lo inet loopback

    auto eth0
    iface eth0 inet dhcp

    auto eth1
    iface eth1 inet static
    address 100.64.0.3
    gateway 100.64.0.1
    netmask 255.192.0.0
    ```

11. 此时，由于dhcp和静态ip的原因，默认路由可能会配错。我们希望默认路由是走eth0的，这样才能正常访问外网。我们可以编写这样一个脚本，可以配置启动项，也可以每次重启时手动执行一遍：

    ```sh
    #!/bin/bash

    expected="default via 10.0.2.2 dev eth0"
    dft=`ip route | grep default | head -n 1`
    dft=`echo $dft`
    if [ ! -z "$dft" ]
    then
	      if [ "$dft" == "$expected" ]
	      then
		        echo "the route seems ok"
		        exit 0
        fi
        sudo ip route del $dft
    fi

    sudo ip route add $expected
    ```

    这里的`10.0.2.2`是NAT网关。如果不是这个地址的话，可以把网卡NAT之外的网卡都去掉，然后再使用`ip route`查看default路由的配置。

12. 接下来我们需要配置local表路由和iptables，统一写在一个脚本里即可：

    ```sh
    #!/bin/bash

    NETWORK="100.96.0.0/11"
    IP="127.0.0.1"
    PORT="8888"

    IP_ROUTE_DEV="dev lo"
    IP_ROUTE_SRC="src 127.0.0.1"

    ip_route_rule=`ip route show table local | grep "$NETWORK" | grep "$IP_ROUTE_DEV" | grep "$IP_ROUTE_SRC"`
    if [ -z "$ip_route_rule" ]
    then
        sudo ip route add local "$NETWORK" $IP_ROUTE_DEV $IP_ROUTE_SRC
    else
        echo "ip route already configured"
    fi

    ip_tables_rule=`sudo iptables --table mangle --list --numeric | grep TPROXY | grep "$NETWORK" | grep "$IP:$PORT"`
    if [ -z "$ip_tables_rule" ]
    then
        sudo iptables -t mangle -I PREROUTING -d "$NETWORK" -p tcp -j TPROXY --on-port="$PORT" --on-ip="$IP"
    else
        echo "iptables already configured"
    fi
    ```

    这里的BIND_IP最好写127.0.0.1，BIND_PORT可以随便填写，只需和后续配置文件一致即可。

13. 可以使用一个简单的脚本文件检查路由和iptables配置是否生效：`sudo ./server.sh 127.0.0.1 8888 hello-world`

    ```python
    #!/usr/bin/env python3
    # -*- coding: utf-8 -*-
    import socket, sys
    import time

    def main():
        HOST, PORT, MSG = sys.argv[1], int(sys.argv[2]), sys.argv[3]

        listen_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        print ('reuseaddr', listen_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1))
        print ('transparent', listen_socket.setsockopt(socket.SOL_IP, socket.IP_TRANSPARENT, 1))

        listen_socket.bind((HOST, PORT))
        listen_socket.listen(10)

        print ('Serving on host %s port %s ...' %(HOST, PORT))
        while True:
            client_connection, client_address = listen_socket.accept()
            local_address = client_connection.getsockname()
            print (client_address, local_address)
            request = client_connection.recv(1024)
            print (request.decode())

            resp = MSG + '\r\n'
            http_response = "HTTP/1.0 200 OK\r\nConnection: Close\r\nContent-Length: " + str(len(resp)) + "\r\n\r\n" + resp
            client_connection.sendall(str.encode(http_response))
            client_connection.close()

    if __name__ == '__main__':
        main()
    ```

    执行`curl 100.123.123.123:1122`，或者其他任何在`100.96.0.0/11`网段下的ip、任何合法的端口号，都可以看到返回响应`hello-world`。同时可以在server日志中看到接受到的连接的源目地址和端口。

### 配置文件

vpws-agent配置文件中加入如下内容，注意listen中的地址和端口号和之前的配置脚本中一致。

```
agent.direct-relay.ip-range 100.96.0.0/11
agent.direct-relay.listen 127.0.0.1:8888
agent.direct-relay.ip-bond-timeout 0
```

由于这个网段非常大，绝对足够正常使用，所以这里的timeout超时时间配置了0，表示不做超时。如果跨机器使用且局域网网段较小，则可以配置10（单位：分钟）或者其他合适的数值。

### 编译和运行

vproxy的网络处理层叫做vfd，vproxy实现了多套vfd实现，最基础的是java内置的channel实现。除此以外还有posix实现和f-stack实现。这里我们需要使用posix实现。

首先安装编译相关的工具：

```
sudo apt-get install build-essential
```

然后进入vproxy源码目录（源码根目录），运行：

```
make vfdposix          # 编译vfdposix库
./gradlew clean jar    # 编译java文件为jar包
```

你可以使用`java -Deploy=HelloWorld -Dvfd=posix -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar`来检查vfd实现是否正常运行。

接下来就可以运行vpws-agent了。

```
sudo java -Deploy=WebSocksProxyAgent -Dvfd=posix -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar "$配置文件路径"
```

### 客户端配置

将DNS服务器配置为`100.64.0.3`。

### 期望结果

1. 在客户端机上任意浏览器中（不打开任何代理选项），直接访问要代理的域名。
2. 在agent日志中能看到DNS解析和代理。并且可以看到绑定ip和域名的日志。
