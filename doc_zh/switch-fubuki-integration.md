# 虚拟交换机模块与fubuki的整合

## 概述

`vproxy`提供了一套较为完整的虚拟交换机实现，支持TCP/IP协议栈；`fubuki`是一个由rust编写的基于tun接口的网络mesh。  
将fubuki的tun接口修改为收发包API，并接入vproxy虚拟交换机，即可完成两者的结合。

## 命令说明

可以在启动vproxy后，使用如下命令查看本文档用到的命令细节：

* `man switch`
* `man vpc`
* `man fubuki`
* `man iface`
* `man switch add`
* `man vpc add-to`
* `man fubuki add-to`
* `man iface list-detail`
* `man iface remove-from`

## 配置

1. 启动vproxy

```
make jar-with-lib
java -Dvfd=posix -jar build/libs/vproxy.jar
```

2. 创建虚拟交换机

```
add switch sw0
```

3. 创建虚拟网络

```
add vpc 1 to switch sw0 v4network 10.99.88.0/24
```

命令解释：

* `add vpc 1` 表示创建`1`号vpc，其vni即为1
* `to switch sw0` 表示加入`sw0`中
* `v4network $.$.$.$/$` 表示该vpc的v4的网段限制

4. 创建fubuki接口

```
add fubuki fbk0 to switch sw0 vni 1 mac 00:11:22:33:44:55 ip 10.99.88.199/24 address $.$.$.$:$ password $
```

命令解释：

* `add fubuki fbk0` 表示创建一个fubuki网口，名为`fbk0`
* `to switch sw0` 表示加入`sw0`中
* `vni 1` 表示该网口默认属于vni为`1`的vpc，即`vpc 1`
* `mac $:$:$:$:$:$` 表示为该网口分配的mac地址。由于fubuki是tun模式，所以需要交换机为其模拟二层报文
* `ip $.$.$.$/$` 表示该网口使用的ip和掩码，可以不指定，不指定则由fubuki为其分配一个ip
* `address $.$.$.$:$` 表示远端fubuki地址和端口
* `password $` 表示fubuki通信使用的密码

## 查看

```
ll iface in switch sw0
```

## 测试

使用任意标准fubuki客户端连接到同一服务，然后ping `fbk0`所绑定的ip

```
PING 10.99.88.199 (10.99.88.199): 56 data bytes
Request timeout for icmp_seq 0
64 bytes from 10.99.88.199: icmp_seq=1 ttl=64 time=92.467 ms
64 bytes from 10.99.88.199: icmp_seq=2 ttl=64 time=90.040 ms
64 bytes from 10.99.88.199: icmp_seq=3 ttl=64 time=92.859 ms
^C
--- 10.99.88.199 ping statistics ---
4 packets transmitted, 3 packets received, 25.0% packet loss
round-trip min/avg/max/stddev = 90.040/91.789/92.859/1.247 ms
```

注意，因为vproxy虚拟交换机需要查询mac，所以第一个回包无法发送，后续报文均应当正常收发。

> 对于tun设备，arp/ns会被转换为特殊的icmp包。

## 删除

```
remove iface fubuki:fbk0 from switch sw0
```
