# Switch

vproxy基于vxlan支持了交换机功能。该功能并非是对标ovs，而是吸收标准vxlan和经典三层交换的思路构建SDN，用于建立VPN（Virtual Private Network）。

本文介绍vproxy Switch功能、用法，以及如何构建VPN。

## 一、功能

Switch还在持续开发中。目前支持如下功能点：

1. vxlan、ethernet、arp包解析
2. mac table（根据src mac和来源iface记录entry）
3. ipv4 arp table（根据arp请求和响应记录entry）
4. 单个vni下的二层广播、多播、单播
5. 支持操作命令（通过stdin或者通过telnet、redis-cli操作）
6. 使用加密的vxlan包

综合来看，功能如下：

1. 对每个VNI划分一块虚拟网络，网络内有独立的mac table和arp table，后续还会支持基于vni的route table
2. 每个VNI内都是一个二层互通的网络
3. 不同VNI互不相通（后续会支持vni之间的路由表）

## 二、用法

使用vproxy的Switch功能需要分三块：

1. vproxy主程序启动Switch：这个是交换机的主体
2. 在跑vxlan的机器可访问的地方启动VXLanAdaptor：这个负责将vxlan包加密发送到switch上，以及将switch过来的包解密送给vxlan口。此外还负责发送心跳保持UDP链路。
3. linux机器上置vxlan网口：这里是vxlan的源头和目的地。

这里用一个简单的拓扑描述一下三者的关系：

```
AdaptorA <----------------> Switch <----------------> AdaptorB
  ^                                                      ^
  |                                                      |
  v                                                      v
VXLAN-A                                               VXLAN-B
```

1. 首先Switch需要是启动状态
2. 当Adaptor启动时，Adaptor会给Switch发心跳包。Switch收到时也会回心跳。类比到经典交换机，此时就像网线插好了一样。
3. VXLAN和Adaptor通信，对于VXLAN设备来说，Adaptor就是一个正常接收vxlan包的设备。
4. 当Adaptor收到VXLAN来的包时，它会做一些简单的校验然后加密发给Switch。
5. 当Adaptor收到Switch来的包时，它会解包、校验，然后将vxlan包发给VXLAN设备。
6. 当Switch收到包时，它会解包、校验，然后根据报文做转发。
7. 由于Adaptor和Switch之间常常会有数层SNAT，所以若Adaptor重启更换源端口，对于Switch来说都是一个不同的网络端点。所以若Adaptor长时间没有推送心跳，则Switch会清空mac table中和这个sock相关的所有记录。

这里注意几点：

1. Switch接受的并不是vxlan包，而是vproxy自行编码的带metadata的加密包。这和初始设计目的是有关的（构建VPN）。
2. 实际和VXLAN设备通信的是Adaptor，它适配了vxlan包和vproxy的加密包。
3. 对于VXLAN设备来说不需要关心vproxy加密包，只管正常收发vxlan包即可。

### 2.1 启动Switch

这一步非常简单：

1. 启动vproxy: `java -jar vproxy.jar`
2.  1. 如果你有redis-cli，建议使用`redis-cli -h 127.0.0.1 -p 16309 -a 123456`
    2. 如果手头没有redis-cli，也可以直接`telnet 127.0.0.1 16309`，然后第一条命令输入`AUTH 123456`
    3. 当然也可以直接在vproxy运行中的程序中输入命令并执行
3. 输入命令创建switch：
    ```
    add switch sw0 address 0.0.0.0:4789 password p@sSw0rD
    ```
    这样即可创建一个名为`sw0`的Switch，绑定在`0.0.0.0:4789`，密码使用`p@sSw0rD`。  
    具体参数详情请输入`man`|`man switch`|`man switch add`查看，或者查阅[command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md)。

这样Switch就创建完毕了。你可以使用如下命令查看现存的vni以及arp/mac table：

```
list-detail vni in switch sw0
list-detail arp in vni 1314 in switch sw0
```

### 2.2 启动VXLanAdaptor

这一步也同样非常简单，只不过要在启动时输入几个参数：

```
java -jar vproxy.jar -Deploy=VXLanAdaptor ...
```

1. `switch={...}`: 填写第一步启动的switch所在机器的ip以及switch的端口号，以ip:port形式填入。其中ip也可以换成域名。
2. `vxlan={...}`: 填写vxlan endpoint的ip:port。其中ip也可以换成域名。从switch收到的vxlan包将会发送到这里填写的地址上。
3. `listen={...}`: 填写监听vxlan包的地址，依旧是ip:port形式。其中ip也可以写0.0.0.0。
4. `password={...}`: 与switch通信时使用的密码，需要和switch创建时使用的密码一致。

### 2.3 配置linux机器

这一步可能稍复杂一些。这里详细说一下流程。

#### 2.3.1 Adaptor和Linux不在同一台机器上

假如Adaptor和Linux机器不在同一台机器上（例如虚拟机里起Linux），那么步骤会比较简单：

1. 用`ip a`找到和Adaptor通信的网口，比方说`eth0`。不确定的话可以`ip route get $IP`查看，其中IP是Adaptor机器的ip
2. 使用`ip link add type vxlan help`查看帮助命令。对于部分较低版本的linux不支持配置vxlan的udp接收端口号，大概形如如下输出的就是不支持的：
    ```
    Usage: ... vxlan id VNI [ { group | remote } ADDR ] [ local ADDR ]
                    [ ttl TTL ] [ tos TOS ] [ dev PHYS_DEV ]
                    [ port MIN MAX ] [ [no]learning ]
                    [ [no]proxy ] [ [no]rsc ]
                    [ [no]l2miss ] [ [no]l3miss ]

    Where: VNI := 0-16777215
          ADDR := { IP_ADDRESS | any }
          TOS  := { NUMBER | inherit }
          TTL  := { 1..255 | inherit }
    ```
3. 如果输出内容包含`dstport`，那么就是支持配置vxlan的udp接收端口号的。
4. 如果不支持配置的话，Adaptor只能配合linux，将listen的监听端口设置为`8472`，否则只需和`dstport`一致即可。
5. 对于不支持配置dstport的老版本，可以这样添加vxlan口：`sudo ip link add "$NAME" type vxlan id "$VNI" remote "$VX_DEST" dev "$VX_DEV"`。  
    `$NAME`是vxlan网口的名称，一般写vxlan0之类的就好  
    `$VNI`是vni号，需要通信的终端选择相同的vni即可
    `$VX_DEST`是Adaptor的地址，不包含端口号（默认只能是8472）
    `$VX_DEV`是刚才第一步找到的udp通信用的网口
6. 对于支持dstport的新版本linux，可以在参数里增加`dstport ${}`，这样Adaptor的监听端口就可以自定义了。其他参数和老版本一致。
7. 无论新版本还是老版本，后续的配置都是一样的。
8. 配置ip：`sudo ip addr add "$IP" dev "$NAME"`。  
    `$IP`是要配置的ip+子网掩码，如果只配置ip默认会加一个/32的掩码，这样是无法主动和外部通信的。由于这里是虚拟网络，只要和现有网段均不同即可，比如`172.16.0.1/24`。  
    `$NAME`是刚才创建的vxlan网口的名称。
9. 把网口up起来：`sudo ip link set "$NAME" up`。
10. 使用`ip a`查看，应当能看到网口处于`UNKNOWN`状态，并且上面配置了ip。
11. 由于我们使用的udp封装使用了vxlan+vproxy外层包装，占用很多字节数，所以最好调整下mtu防止出现各种奇怪的问题：`sudo ip link set "$NAME" mtu 1350`。

到此已经配置完成。

#### 2.3.2 Adaptor和Linux在同一台机器上

有的时候我们为了方便部署，或者有其他一些考虑，需要将Adaptor和Linux部署在同一台机器上。这也是可以实现的，虽然步骤麻烦一些，但是这里反而可以更好地自动化。

我写了一个脚本，修改脚本开头几行，然后直接执行即可完成vxlan的配置。然后按照脚本末尾打印的java命令启动vproxy即可。

脚本可以任意重复执行多次，如果某些资源误删了（比如iptables规则），重新执行即可恢复。

脚本流程如下：

1. 创建一个netns
2. 创建一对veth，把其中一个veth放到netns里
3. 在netns里，对于进入netns的vxlan包，修改源目地址，送回netns外（送回给Adaptor进程或者送回给linux vxlan驱动）
4. vxlan的udp口绑netns外面的veth
5. java进程配合iptables规则进行监听

该脚本所有参数都在开头标出，有2个参数是肯定需要修改的，其余保持原样或者按需修改均可，但是所有参数最好不要有重复。

如果要添加、修改、删除vxlan口的ip，直接正常修改即可，其他配置均无需修改。

```shell
#/bin/bash

# values you might want to change:
VNI="1314"            # network id
VX_IP="172.16.0.1/24" # the ip and mask to bind on your vxlan dev

# values you need to set when launching vproxy:
SWITCH="{remote server address}"
PASSWORD="{your password}"

# values that you may simply ignore:
VX_DEV="vxlan-vproxy"      # dev name of vxlan port
MAIN_DEV="veth-main"       # the veth on machine
NS_DEV="veth-ns"           # the veth on ns
VX_REMOTE="10.255.255.254" # the address on ns
VX_LOCAL="10.255.255.253"  # the address on machine
VX_RCV_PORT="8472"         # the port for linux to receive vxlan packet
NS="vxlan-vproxy"          # netns name
ADAPTOR_RECV="14789"       # vproxy port to receive vxlan packets
ADAPTOR_SEND="10001"       # vproxy port to send vxlan packets to

# create netns
res=`ip netns list | grep "$NS"`
if [ -z "$res" ]
then
	echo "creating netns $NS ..."
	sudo ip netns add "$NS"
else
	echo "netns $NS already created"
fi

# create main and ns dev
res=`ip link list | grep "$MAIN_DEV"`
if [ -z "$res" ]
then
	echo "creating $MAIN_DEV and $NS_DEV ..."
	sudo ip link add "$MAIN_DEV" type veth peer name "$NS_DEV"
else
	echo "$MAIN_DEV already created"
fi

# check ns dev and move it into ns
res=`ip link list | grep "$NS_DEV"`
if [ -z "$res" ]
then
	echo "check whether $NS_DEV in namespace"
	res=`sudo ip netns exec "$NS" ip link list | grep "$NS_DEV"`
	if [ -z "$res" ]
	then
		echo "UNEXPECTED CONDITION: $NS_DEV not in netns $NS"
		exit 1
	else
		echo "$NS_DEV is in netns $NS"
	fi
else
	echo "moving $NS_DEV into namespace $NS ..."
	sudo ip link set $NS_DEV netns "$NS"
fi

# assign ip to main dev
res=`ip a | grep "$VX_LOCAL"`
if [ -z "$res" ]
then
	echo "assigning ip $VX_LOCAL to $MAIN_DEV"
	sudo ip addr add "$VX_LOCAL/30" dev "$MAIN_DEV"
else
	echo "$VX_LOCAL already assigned"
fi

# assign ip to ns dev
res=`sudo ip netns exec "$NS" ip a | grep "$VX_REMOTE"`
if [ -z "$res" ]
then
	echo "assigning ip $VX_REMOTE to $NS_DEV"
	sudo ip netns exec "$NS" ip addr add "$VX_REMOTE/30" dev "$NS_DEV"
else
	echo "$VX_REMOTE already assigned"
fi

# enable
sudo ip link set "$MAIN_DEV" up
sudo ip netns exec "$NS" ip link set "$NS_DEV" up

# add iptables rule to redirect netflow to rcv port
res=`sudo ip netns exec "$NS" iptables -n -t nat --list | grep "$VX_REMOTE.*udp.*dpt:$VX_RCV_PORT.*to:$VX_LOCAL:$ADAPTOR_RECV"`
if [ -z "$res" ]
then
        echo "adding iptables rule to redirect packets to rcv port ..."
        sudo ip netns exec "$NS" iptables -t nat -I PREROUTING -p UDP --destination "$VX_REMOTE" --dport "$VX_RCV_PORT" -j DNAT --to-destination="$VX_LOCAL:$ADAPTOR_RECV"
else
        echo "iptables rule to redirect packets to rcv port already added"
fi

# add iptables to fix source address for adaptor rcv
res=`sudo ip netns exec "$NS" iptables -n -t nat --list | grep "$VX_LOCAL.*$VX_LOCAL.*udp.*dpt:$ADAPTOR_RECV.*to:$VX_REMOTE"`
if [ -z "$res" ]
then
	echo "adding iptables to fix source address for adaptor rcv ..."
	sudo ip netns exec "$NS" iptables -t nat -I POSTROUTING -p UDP --destination "$VX_LOCAL" --dport "$ADAPTOR_RECV" --source "$VX_LOCAL" -j SNAT --to-source="$VX_REMOTE"
else
	echo "iptables rule to fix source address for adaptor rcv already added"
fi

# add iptables rule to redirect netflow from snd port
res=`sudo ip netns exec "$NS" iptables -n -t nat --list | grep "$VX_REMOTE.*udp.*dpt:$ADAPTOR_SEND.*to:$VX_LOCAL:$VX_RCV_PORT"`
if [ -z "$res" ]
then
        echo "adding iptables rule to redirect packets from snd port ..."
        sudo ip netns exec "$NS" iptables -t nat -I PREROUTING -p UDP --destination "$VX_REMOTE" --dport "$ADAPTOR_SEND" -j DNAT --to-destination="$VX_LOCAL:$VX_RCV_PORT"
else
        echo "iptables rule to redirect packets from snd port already added"
fi

# add iptables to fix source address for adaptor snd
res=`sudo ip netns exec "$NS" iptables -n -t nat --list | grep "$VS_LOCAL.*$VS_LOCAL.*udp.*dpt:$VX_RCV_PORT.*to:$VX_REMOTE"`
if [ -z "$res" ]
then
	echo "adding iptables to fix source address for adaptor snd ..."
	sudo ip netns exec "$NS" iptables -t nat -I POSTROUTING -p UDP --destination "$VX_LOCAL" --dport "$VX_RCV_PORT" --source "$VX_LOCAL" -j SNAT --to-source="$VX_REMOTE"
else
	echo "iptables rule to fix source address for adaptor snd already added"
fi

# enable ip_forward in ns
sudo ip netns exec "$NS" sysctl -w net.ipv4.ip_forward=1

# create vxlan
res=`ip a | grep "$VX_DEV"`
if [ -z "$res" ]
then
	echo "creating $VX_DEV ..."
	portParams=""
	if [ "$VX_RCV_PORT" != "8472" ]
	then
		portParams="dstport $VX_RCV_PORT"
	fi
	sudo ip link add "$VX_DEV" type vxlan id "$VNI" remote "$VX_REMOTE" $portParams dev "$MAIN_DEV"
else
	echo "$VX_DEV already created"
fi

# assign ip to vxlan dev
res=`ip a | grep "$VX_IP"`
if [ -z "$res" ]
then
	echo "assigning $VX_IP to $VX_DEV ..."
	sudo ip addr add "$VX_IP" dev "$VX_DEV"
else
	echo "$VX_IP already added"
fi

# enable vxlan dev
sudo ip link set "$VX_DEV" up

# disable cksum offloading
sudo ethtool -K vxlan-vproxy tx off rx off

# set mtu
sudo ip link set vxlan-vproxy mtu 1350

# enable ip_forward (this command has nothing to do with deploying vxlan and adaptor, but it's so useful for what you might want to do later)
sudo sysctl -w net.ipv4.ip_forward=1

echo "corresponding java command is:"
echo "java -jar vproxy.jar -Deploy=VXLanAdaptor switch=$SWITCH vxlan=$VX_REMOTE:$ADAPTOR_SEND listen=$VX_LOCAL:$ADAPTOR_RECV password=$PASSWORD"
```

关于上述配置的思考：

根据我的尝试和猜测，1）linux在接收vxlan包的时候，需要包从指定的网卡进来才会进行解析。2）linux不会处理从外部网卡进来的“自己到自己”的报文（即从网卡外面进来但是源目ip都是本机存在的ip）。3）当配置了vxlan之后，linux会监听0.0.0.0:8472（或者其他指定的vxlan的udp口），我们的进程是无法在上面监听的。

为了解决1），需要让java进程发出的包先从网卡出去，然后再绕回来。  
为了解决2），需要配置NAT，让源ip不是本机ip。
为了解决3），需要配置NAT，让目的ip不是vxlan的udp。

这里有几种思路。比较简单的思路是直接开个netns，配一对veth，然后把Adaptor跑在ns里。这样对于网络栈来说Adaptor就是在“另一台机器”上的了。  
但是如果要让netns访问外部Switch，还需要额外配置一堆路由规则。并且Adaptor跑在ns里也不够直接，维护起来稍微麻烦一些。

还有比较简单的方案就是在ns里起个udp中转。送进ns之后再用另一个sock送回外面。这样ns里面就不用通Switch了。这个比上一种好一些，但是还是需要一个进程跑在里面，性能也会差很多，依赖也多了一个。

最终使用iptables的方法，规则都是无状态的，性能有保障，而且简单直接可以写成脚本自动化。

## 三、构建VPN

按照上述的步骤搭建Switch和两组（Adaptor+Linux）后，应当可以在linux上ping通对方的ip。

如果不通，可以观察Adaptor日志，是否有vxlan包进来，以及是否和Switch连通。观察Switch的日志以及arp表记录，可以排查是不是某一端配置错误导致没连上。

后面的步骤需要保证至少各端点能ping通。

### 我们要做什么？

我们的核心目的是，让不同内网机器能够通过Switch连接在一起，互相能够连通。

最简单的方案，是需要加入的机器全部起linux虚拟机并进行上面的配置。这样大家都在同一个二层下面。然后再约定好各自的ip地址或者网段，每个人自行配置路由。这样自然可以互相访问。

但是这个方案实在过于麻烦，需要所有人接入，所有人共享整个网络的所有信息。如果人数多起来，这种组网方式是不现实的。（当然如果人数少这种方法却是最方便的）。

所以我们需要把上述步骤搭建的机器作为路由器：

```
               PC.a 100.64.0.3                          192.168.56.2 PC.x
                |                                                      |
                | 172.16.0.1                                172.16.0.2 |
10.64.0.0/10  ROUTER1 ------------------- SWITCH ------------------- ROUTER2 192.168.56.0/24
                |                                                      |
                |                                                      |
               PC.b 100.64.0.1                          192.168.56.1 PC.y
```

>别问我为啥ip段这么奇葩。我之前玩别的东西时候剩下的虚拟机配置直接拿来用了。。。

### 静态路由

这时如果机器少，可以采用静态路由的方法完成配置。

1. PC.a要访问PC.x，那么PC.a需要把默认网关指向172.16.0.1，这样路由器才能有机会处理它的包。
2. ROUTER1要把目的为192.168.56.0/24段的包都从vxlan口发出去，发给ROUTER2(172.16.0.2)。
3. ROUTER2看到这个包，因为目的地址在同一个二层，所以直接就找到机器了，发送给PC.x(192.168.56.1)。
4. PC.x为了回应PC.a的请求，就需要把默认网关指向172.16.0.2，原因和(1)同理。
5. ROUTER2要把目的为100.64.0.0/10段的包都从vxlan口发出去，发给ROUTER1(172.16.0.1)。
6. ROUTER1的处理类似(3)，直接就丢给PC.a了。

直接配置：

在ROUTER1上：

```shell
sudo sysctl -w net.ipv4.ip_forward=1 # 记得先打开ip_forward
sudo ip route add 192.168.56.0/24 via 172.16.0.2
```

在ROUTER2上：

```shell
sudo sysctl -w net.ipv4.ip_forward=1 # 记得先打开ip_forward
sudo ip route add 100.64.0.0/10 via 172.16.0.1
```

然后在PC.a上 ping 192.168.56.2，会发现已经能够连通了。

### rip

当机器比较多的时候，静态路由非常麻烦。我们需要借助至少一种路由协议。

没接触网络的可能不太熟悉各路由协议。不过不要紧，rip是最简单的自动路由协议协议了。它工作原理是这样：

1. 配置路由各个端口连接的网段
2. 让路由之间自动互相发现

就是这么简单。但是这只能用在路由较少的情形下。如果路由过多，每次路由同步都会对网络造成负担。但是在我们的场景下似乎是一个非常好用的解决方案。

在Linux上使用rip一般是借助quagga。quagga是一个非常常用常见且泛用的路由项目。

注意，新老版本linux在安装和使用quagga时可能略有差异。

**安装quagga：**

```
sudo apt-get install quagga
```

**编写zebra配置文件：**

```
sudo vim /etc/quagga/zebra.conf
```

```
hostname vproxy
password zebra
```

**编写rip配置文件：**

```
sudo vim /etc/quagga/ripd.conf
```

```
hostname vproxy
password zebra
router rip
network 192.168.56.0/24
network 172.16.0.0/24
log stdout
```

注意，这里的`network`按实际情况填写。

如果安装后在`/etc/quagga`中能看到`daemons`文件，那么说明你是老系统，否则是新系统。

<details><summary>对于老系统</summary>
<br>

**在daemons中启用zebra和ripd：**

```
sudo vim /etc/quagga/daemons
```

这里修改如下2项即可，其余为no的可以不动。

```
zebra=yes
ripd=yes
```

**启动ripd：**

```
sudo service quagga restart
```

</details>

<details><summary>对于新系统</summary>
<br>

**启动ripd：**

```
sudo systemctl restart ripd
```

</details>

**检查路由：**

在quagga机器上使用`telnet 127.0.0.1 2602`登陆vty。输入配置的密码（也就是`zebra`）后即可登入。

输入如下命令可以查看学习到的路由：

```
show ip rip
```

当所有机器上配置的路由在一个列表中全部出现时，4台PC应当都能互相ping通。

到此，我们的路由就算搭建完成了。

### 网关配置

刚才在说静态路由的时候就提到过，客户端需要将默认网关配置为Linux机器。实际上并不一定是默认路由，客户端可以指定一个网段发往vxlan机器，但是这样维护起来不太方便。最好就是把包全部发给vxlan机器，然后又它来决定哪些流量走vxlan口出去，哪些走更上一级的网关。

在一个局域网中只要有一台这样的机器即可。

比方说家庭网络中可能会有一台x86的群晖，上面可以跑虚拟机。我们可以把它作为默认网关。

我们要创建一台虚拟机，具体操作网上找下就有，鼠标点两下的事。  
这里关键点是，虚拟机网卡默认都是桥接的，也就是说对于上层路由器来说它就是一个独立的网络设备。这正是我们需要的。

在这台虚拟机上搭建上文描述的vxlan、adaptor和quagga ripd。然后，在整个局域网内，所有机器的默认网关均指向这台虚拟机。这样一来，这个网络就成功暴露在switch上了。其他网络可以自由地加入。

### 使用场景

#### 1. 在家控制公司电脑

许多公司提供了VPN连接办公网，在连接vpn之后远程连接办公机。若使用本文描述的方案，则可以跳过VPN连接的步骤，直接登入。

#### 2. 异地多个网络互通

两个局域网网络互通，如果要求高性能，则通常会考虑专线。或者也可能在网关上配置VPN。这里使用本文的方案其实就和使用VPN一样。

#### 3. 跨越防火墙

通过switch的中转，可以连接防火墙两侧的网络，并且使用时网络互通就像没有防火墙存在一样。

## 最后

不管干啥，都千万别忘了开这条命令！！！！

```
sudo sysctl -w net.ipv4.ip_forward=1
```
