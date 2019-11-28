# 如何在F-Stack上运行vproxy

## 简介

### 什么是DPDK、F-Stack？

DPDK是一个用来直接在用户态快速操作网络包的库。

[https://www.dpdk.org](https://www.dpdk.org)

F-Stack是一个基于DPDK和FreeBSD的网络库，提供了Posix-like的网络API，通过用户态FreeBSD协议栈和DPDK直达网卡，性能比传统应用走内核系统调用会高出很多。

[https://github.com/F-Stack/f-stack](https://github.com/F-Stack/f-stack)

### 为何要将vproxy运行在F-Stack上？

这并非为了实用或者性能的最终目标，而是一次尝试或者一个poc，为了证实java程序可以运行在dpdk、f-stack上，并且可以充分利用f-stack的能力运行已有的代码。以及证实vproxy的`vfd`封装可以完全对上层透明。

另外，由于vproxy设计之初就强调所有配置运行时可变更而不用重启或者新开进程，所以这也让vproxy非常适合f-stack。可以期待后续的实际应用。

### 注意点

1. DPDK和F-Stack都需要编译为动态链接库（带`-fPIC`编译）
2. link参数里要带上`--no-as-needed`，将dpdk全部链进去
3. 最好gcc版本别太高，编译DPDK和F-Stack时可能会有很多类型检查错误，还得去改CFLAGS，很麻烦

### 本文会做哪些事？

1. 部署虚拟机。
2. 编译并配置DPDK、F-Stack、vproxy。
3. 运行vproxy的HelloWorld程序。
4. 使用F-Stack提供的工具查看运行状态。

## 让我们开始吧！

### 1. 虚拟机和镜像

这里使用VirtualBox（推荐）和Ubuntu 14.04.6。注意，比较新的gcc编译DPDK/F-Stack会有类型检查报错，为了避免麻烦，建议不要用高版本的发行版。

下载：

* [virtualbox](https://www.virtualbox.org/)
* [ubuntu-14.04.6-server-amd64.iso](http://mirrors.163.com/ubuntu-releases/14.04.6/ubuntu-14.04.6-server-amd64.iso)

### 2. 配置虚拟网络

在Virtualbox的“管理”菜单里找到“主机网络管理器”，创建一块网卡。下面的配置类似于：

```
vboxnet0
192.168.56.1
255.255.255.0
```

顺便，为了配置方便，建议保证dhcp是开启的。

![vnet](https://github.com/wkgcass/vproxy/blob/master/doc_assets/002-fstack-how-to-vnet.png?raw=true)

### 3. 新建虚拟机

这里类型选择“Linux”，版本选择“Ubuntu (64-bit)”，其他配置随意。

创建后进入“设置”页面。“存储”配置的光驱中指定ubuntu安装镜像。

![storage](https://github.com/wkgcass/vproxy/blob/master/doc_assets/003-fstack-how-to-storage.png?raw=true)

### 4. 网络配置

这里比较关键。我先解释一下这一步要做什么，以及为什么这么配置。

我们会使用3张网卡：
1. 第一张使用NAT，用于从虚拟机内访问外网。
2. 第二张使用Host-Only网络，用于虚拟机和宿主机互通，并且可以用于访问DPDK应用进程。
3. 第三张使用Host-Only网络，用于起DPDK应用进程。

其中，Host-Only的网卡，“界面名称”的选择刚才配置的虚拟网卡vboxnet0。并且，第三张网卡需要点开高级设置，控制芯片里选择“Intel PRO/1000 MT 服务器 (82545EM)”。

![nic](https://github.com/wkgcass/vproxy/blob/master/doc_assets/004-fstack-how-to-nic.png?raw=true)

看到这里你可能有几个疑惑：

**1. 要访问外网/和宿主机互通 为什么不使用桥接？**

有的网络环境并不允许任意分配ip，比如大部分公司的办公网要求mac白名单或者使用个人账号登录才能访问外网。使用NAT可以保证当宿主机通外网时虚拟机一定也通外网。

**2. 为什么需要单独一张网卡用于虚拟机和宿主机互通？**

第一张网卡使用NAT后，宿主机和虚拟机是无法互通的，这时候必须开一个新网卡。

**3. 为什么需要单独网卡给DPDK？**

因为只有从内核将网卡卸载，DPDK才能使用，所以必须是单独的网卡。

**4. 为什么给DPDK的网卡要跑Host-Only网络？不能桥接吗？**

不使用桥接有一个原因是第一条，因为网关限制，桥接情况不一定能通网。除此之外，由于DPDK完全将网络交给了用户，所以一旦程序出现bug有可能会把网络搞崩（之前遇到过类似事情）。  
不使用“内部网络”原因也很简单，用了“内部网络”就没办法从宿主机debug了。

**5. 宿主机网络里的其他机器能否访问DPDK应用？**

如果按上面的配置来搞，不能访问，因为它仅在本地的虚拟网络里。如果需要其他机器访问DPDK应用，就只能用桥接了，网络环境需要自行保证。

### 5. 配置清单

![list](https://github.com/wkgcass/vproxy/blob/master/doc_assets/005-fstack-how-to-list.png?raw=true)

### 6. 安装操作系统

启动虚拟机，安装操作系统。询问主网络接口时选择eth0（也就是刚才配了NAT的那个网卡）。  
默认软件里建议选择ssh server（用空格键选择，回车进入下一步）。如果没安装，后续可以apt-get install openssh-server。  
其他步骤就是一路点“是”即可。

最后安装完了自动重启，输入用户密码登录。

### 7. 配置网卡

sudo vim /etc/network/interfaces

在文件末尾添加

```
auto eth1
iface eth1 inet dhcp
```

注意，如果一开始虚拟网络里没开dhcp，这里就要自己配静态ip。

配置后重启`sudo reboot`

### 8. 检查ip和sse

使用`ip a`查看分配到eth1上的地址。

比方说，我这里看到的是`192.168.56.102`。后续操作建议都使用ssh进行，可以从宿主机连到eth1上。

还需要检查是否支持sse4，`cat /proc/cpuinfo | grep sse`，应该能看到sse4\_1 sse4\_2的字样。

### 9. 安装软件

执行`sudo /bin/bash`进入root，后续几乎所有操作都需要root权限

```
apt-get install -y net-tools build-essential libnuma-dev python libssl-dev git gawk
wget https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.5%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.5_10.tar.gz
mkdir /data
tar zxf OpenJDK11U-jdk_x64_linux_hotspot_11.0.5_10.tar.gz --directory /data/
git clone https://github.com/F-Stack/f-stack.git /data/f-stack --depth=1 --branch=master
git clone https://github.com/wkgcass/vproxy.git /data/vproxy --depth=1 --branch=master
```

修改环境变量 `vim ~/.bashrc`，末尾添加：

```
export JAVA_HOME=/data/jdk-11.0.5+10
export PATH=$PATH:$JAVA_HOME/bin
export FF_PATH=/data/f-stack
export RTE_SDK=/data/f-stack/dpdk
export RTE_TARGET=x86_64-native-linuxapp-gcc
export FF_DPDK="$RTE_SDK/$RTE_TARGET"
export CONF_CFLAGS=-fPIC
export LD_LIBRARY_PATH="$FF_PATH/lib:$FF_DPDK/lib:/data/vproxy/src/main/c"
```

添加后执行`source ~/.bashrc`

### 10. 编译和配置DPDK

`cd /data/f-stack/dpdk`

我们需要配置成动态链接库。

`vim config/common_base`  
将里面的`CONFIG_RTE_BUILD_SHARED_LIB=n`  
修改为`CONFIG_RTE_BUILD_SHARED_LIB=y`

然后使用DPDK提供的交互工具编译和初始化环境

```
cd usertools
./dpdk-setup.sh
```

输入序号执行命令。不同版本支持的命令列表不同，序号也就不同，这里给出18.11.5版本的序号，其他版本可以按后面的说明文字进行匹配自行判断。

**1. 编译：**

```
[15] x86_64-native-linuxapp-gcc
```

**2. 初始化IGB UIO模块**

```
[18] Insert IGB UIO module
```

**3. 配置巨页**

```
[21] Setup hugepage mappings for non-NUMA systems
```

这里输入适当的大小，因为只是测试，所以随便写一个，比如512

**4. 初始化网卡**

```
[24] Bind Ethernet/Crypto device to IGB UIO module
```

可以看到类似这样的输出：

```
0000:00:03.0 '82540EM Gigabit Ethernet Controller 100e' if=eth0 drv=e1000 unused=igb_uio *Active*
0000:00:08.0 '82540EM Gigabit Ethernet Controller 100e' if=eth1 drv=e1000 unused=igb_uio *Active*
0000:00:09.0 '82545EM Gigabit Ethernet Controller (Copper) 100f' if=eth2 drv=e1000 unused=igb_uio
```

这里我们选择第三张网卡，也就是输入`eth2`

配置完成后按`ctrl-c`退出。

### 11. 编译和配置F-Stack

`cd /data/f-stack/lib`

修改Makefile（`vim Makefile`），改为编译成动态链接库：  
找到`ar -cqs $@ $*.ro ${HOST_OBJS}`  
修改为`${CC} -shared -o libfstack.so $*.ro -fPIC ${HOST_OBJS}`  
保存后编译：`make`

修改配置文件：

```
cd /data/f-stack
cp config.ini /etc/f-stack.conf
vim /etc/f-stack.conf
```

**1. lcore_mask**

修改其中的`lcore_mask`：  
如果虚拟机只有一个核，那么设置`lcore_mask=1`  
如果虚拟机有多个核，那么设置一个高位的核，比如第二个核就是`2`，第三个核就是`4`

**2. numa_on**

设置为`0`

**3. port0**

找到`[port0]`，在下面可以设置ip地址、子网掩码、广播地址、网关地址。

这里的ip地址只要从刚才创建的vboxnet0对应网段里随便找一个ip即可，掩码和广播地址都是跟网段相关的。网关地址填写宿主机上这个虚拟口的ip地址。

比如我的配置：

```
[port0]
addr=192.168.56.40
netmask=255.255.225.0
broadcast=192.168.56.255
gateway=192.168.56.1
```

### 12. 编译vproxy

```
# 编译java部分
cd /data/vproxy
./gradlew clean jar

# 编译c部分
cd src/main/c
./make-fstack.sh
```

### 13. 运行vproxy HelloWorld

```
cd /data/vproxy
java -Deploy=HelloWorld -Dfstack="--conf /etc/f-stack.conf" -Djava.library.path="./src/main/c" -jar build/libs/vproxy.jar
```

正常的话应当能够看到如下打印：（后续版本更新后可能打印有所变化，不过大概应当如此）

![print](https://github.com/wkgcass/vproxy/blob/master/doc_assets/006-fstack-how-to-print.png?raw=true)

注：程序可能会在下面这个位置卡一段时间，这是正常现象，稍微等待即可，可能半分钟到两分钟不等（另外请确认/etc/f-stack.conf的lcore_mask配置是否符合本文）。

```
EAL: PCI device 0000:00:09.0 on NUMA socket -1
EAL:   Invalid NUMA socket, default to 0
EAL:   probe driver: 8086:100f net_e1000_em
```

### 14. 测试

因为f-stack无法从进程内部访问自己，所以HelloWorld进程没办法自测试，可以从宿主机或者虚拟机里使用curl和nc进行测试。

访问HTTP Server：

```
curl 192.168.56.40:8080/hello
# 输出：
Welcome to vproxy 1.0.0-BETA-6-DEV.
Your request address is 192.168.56.102:47140.
Server address is 192.168.56.40:8080.
```

访问UDP EchoServer：  
`nc -u 192.168.56.40 8080`

这是一个echo server，输入任何内容都会原样返回。

注：这里的ip地址是刚才在`/etc/f-stack.conf`里配的地址。

### 15. f-stack工具

```
cd /data/f-stack/tools
```

修改编译文件

`vim prog.mk`

找到`-ldpdk`那一行，在link选项最前面加上--no-as-needed  
修改后的这一行为：

```
FF_PROG_LIBS+= -L${FF_DPDK}/lib -Wl,--no-as-needed,--whole-archive,-ldpdk,--no-whole-archive
```

保存退出后执行`make`

保持上面vproxy运行的状态，运行f-stack的netstat工具：

```
cd sbin
./netstat -an
```

应当能看到如下输出：

```
Active Internet connections (including servers)
Proto Recv-Q Send-Q Local Address          Foreign Address        (state)
tcp4       0      0 *.8080                 *.*                    LISTEN
udp4       0      0 *.8080                 *.*
udp4       0      0 *.*                    *.*
```

注：工具可能会时不时的报错，稍等再重试即可。
