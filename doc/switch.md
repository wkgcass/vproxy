# Switch

## Intro

vproxy implemented a layer-3 stackable virtual switch, supporting the following features:

* virtual networks based on vxlan
* l2 broadcast and unicast (multicast is handled as broadcast)
* ipv4 and ipv6
* mac table based on transferred ethernet packets
* arp table based on arp or ndp requests/responses
* active arp/ndp unicast or broadcast
* virtual ips (which supports arp/ndp and icmp ping)
* bind tap devices into vrf
* bare vxlan socks
* encrypted vxlan link to another switch
* bare vxlan link to another switch
* route packets from one vrf to another vrf
* route packets to a specific ip address

and the following security features:

* bare vxlan outer source ip white/black list
* forbid arp and ndp out of the configured cidr
* access control for encrypted vxlan link (username and password)

vproxy is a zero-dependency networking tool, which provides lb, socks5, dns, server-group (with health check) and may other utilities.  
It's easy to read, learn or modify since it's zero-dependency, all codes are simple java code with a little piece of optional jni extension.  
Here we talk about SDN switches, it's really easy for you to modify the networking stack, all you need is in one file `Switch.java`.  
Packets for common protocols in L3 are parsed in `vpacket` package.  
Interfaces are placed in `vproxy.iface` package.

Ok, Let's play with it!

## Words

* vrf: The switch is a layer 3 virtual switch, networks are divided by vrfs.
* vrf: Id of a virtual network.
* ns: Linux namespace. Here I mean `netns` (networking namespace) particularly.
* tap: Tap devices, which can transfer l2 packets between kernel net stack and a user space program (in this case, the vproxy application).

## Basic

### 0. prepare the environment

On Linux, nothing to do in this step.

On MacOS, run `brew cask install tuntap`, which would install the `tuntap` kernel extension. You can see these character files under `/dev/` directory: `tap0`, `tap1`, ..., `tap10`, ... devices. Use these names in vproxy (without `/dev/` prefix).

On Windows, follow these steps:

1. Download OpenVPN installer
2. If you do not want to use OpenVPN, you may remove all checks except the `TAP Driver` when installing.
3. Go to `Network and Sharing Center` > `Change adapter settings` (on the left side)
4. Rename the `TAP-Windows Adaptor` to `tap0`
5. When adding tap devices to vproxy, use the above name

For more info, you may check the `Out-Of-Date` article `ManagingWindowsTAPDrivers – OpenVPN Community`, it seems the web page cannot be directly accessed (maybe because of a check to the referrer header), so use a google [search page](https://www.google.com/search?q=https%3A%2F%2Fcommunity.openvpn.net%2Fopenvpn%2Fwiki%2FManagingWindowsTAPDrivers&oq=https%3A%2F%2Fcommunity.openvpn.net%2Fopenvpn%2Fwiki%2FManagingWindowsTAPDrivers) instead.  
It's a little bit old, but there are some useful info (which I referred to when adapting the Windows implementation).  
In the article, chapter `Manual configuration of the TAP-Windows adapter` tells you how to configure a tap adapter, and chapter `Installing and uninstalling TAP-drivers` tells you how to add/remove a tap adapter.

You can also use the bat scripts in `C:\Program Files\TAP-Windows\bin` to add a new tap device. It's recommended to rename the new devices to short names before using.

### 1. create the switch

The following commands should work properly in linux.

#### 1) set up java env

Download OpenJDK 11 and make sure `$JAVA_HOME` env variable is pointed to the jdk directory (which is the directory you can see the `bin` dir which contains `java` executable file).

#### 2) make vproxy

make sure you have `build-essential` installed.

```
git clone https://github.com/wkgcass/vproxy
cd vproxy
make
make vfdposix
```

If you are using windows, it's recommended to use `msys2` and `mingw64` environment. And compile the `vfdwindows` with:

```
make vfdwindows
```

#### 3) check

Run the HelloWorld program to check if things went ok:

```
java -Dvfd=posix -Djava.library.path=./base/src/main/c -Deploy=HelloWorld -jar build/libs/vproxy.jar
```

Check the output, and play with it with `telnet/curl/nc` if you want. Then `ctrl-c` to quit.

On windows, use `-Dvfd=windows` instead of `-Dvfd=posix`.

#### 4) run

```
sudo java -Dvfd=posix -Djava.library.path=./base/src/main/c -jar build/libs/vproxy.jar
```

Sometimes you may have to use `sudo env PATH=$PATH` prefix.

You should see some output, and you can write things into the stdin.

Note: on windows, use `-Dvfd=windows` instead.

#### 5) use redis-cli to manage the vproxy (optional)

```
apt-get install redis-tools
```

Use redis-cli to connect to the vproxy instance, you will get much better experience with the help of redis-cli.

```
redis-cli -h 127.0.0.1 -p 16309 -a 123456
```

#### 6) check commands (optional)

Use the following command to see what you can do:

```
man
man switch
man switch add
man switch add-to
```

All commands are supported by the `man` command.

For more info about the commands, visit [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md).

#### 7) create and check

```
add switch sw2 address 0.0.0.0:18472
"OK"

list-detail switch
"sw2 -> event-loop-group (worker-elg) bind 0.0.0.0:18472 mac-table-timeout 300000 arp-table-timeout 14400000 bare-vxlan-access (allow-all)"
```

### 2. vrf

You need to create vrfs inside a switch before using it. The name of the vrf is the id number of it.

For security consideration, vproxy switch requires you to specify a network, ips in arp or ndp neighbor solicitation/advertisement packets are only allowed in the configured network.

```
add vrf 2 in switch sw0 v4network 172.16.2.0/16 v6network fd00::200/120
```

You may omit the `v6network`, which means disallowing ipv6 ndp neighbor solicitation/advertisement.  
Also note that this config cannot be modified. You can only remove and re-create. In this case, all synthetic ips will be lost because they are strictly attached to the vrf.

### 3. tap

Create or open a tap device. On linux, the tap name could be a pattern, e.g. `tap%d`. The generated tap device name will be returned, which is the tap shown in the `ip a` or `ip link` command.

Any tap device should bond to a vrf, so you need to specify the `vrf` when creating.

```
add tap tap1 to switch sw0 vrf 2
```

Then you can see the `tap1` with `ip link`. Operate the device freely.

You may specify a `post-script` when adding the tap device. The script will be called after the tap device is created/enabled.

```
add tap tap1 to switch sw0 vrf 2 post-script /root/up-tap1.sh
```

### 4. bare vxlan socks

This is allowed as default. Note that you might set a `SecurityGroup` when creating the switch to control who can access the switch using bare vxlan socks. You may also update the `SecurityGroup` anytime you want.

However, when the vproxy receives a vxlan packet, it records the remote address, and packets will be sent to that address. So make sure the sending and receiving port are the same. If you are using a linux vxlan device, set `port 8472 8473` when using `ip link add` will do the job.

Also, the sending vxlan packet's vrf is set to the last received vxlan packet vrf from the sock. If you want to connect two switches, see the following method.

### 5. bare vxlan link to another switch

This allows two switches to connect to each other and handle packets from all vrfs instead of just one when not configured (which will be handled as a basic bare vxlan sock).

This requires two switches be able to directly talk each other and will use the listening udp sock to send packets to each other.

Let's assume we have two switches `sw1` and `sw2`.

One `sw1`

```
add switch sw2 to switch sw1 address 100.64.0.4:18472
```

One `sw2`

```
add switch sw1 to switch sw2 address 100.64.0.1:18472
```

Then packets will go freely between two switches.

Also, you may use this feature to expose this switch to a bare vxlan sock. To do this, you have to specify a flag to the command:

```
add switch bare to switch sw1 address 192.168.56.3 no-switch-flag
```

### 6. encrypted vxlan

This allows a switch to connect to another (usually public) switch with an encrypted link. To achieve this, you need to configure `user`s on the switch to be connected to, and configure the link on the switch to connect.

On the connected switch (usually a switch on public network), which could be called the `server` side:

```
add user to-vrf2 to switch sw0 vrf 2 password p@sSw0rD
```

On the switch to connect (usually a switch behind a NAT router), which could be called the `client` side:

```
add user-client to-vrf2 to switch sw0 password p@sSw0rD vrf 2 address 192.168.77.1:18472
```

As you can see, vrf can be set both on the `server` and `client` sides, and they can be set to different values. The packets will be transformed on the server side.  
To reduce any possible information leak of the server, the server will not send any packet to the client before receiving at least one vxlan packet from client, which will carry the vni of the client, then the server will be able to set the vni before sending packets to the client.

Note that the user name cannot < 3 chars and cannot > 8 chars and only a-zA-Z0-9 allowed.

After configuration, you should see an alert tell you the switches are connected.

### 7. route to another vrf

You may add route rules to redirect packets to another vrf.

```
add route to172.17 to vrf 1314 in switch sw0 network 172.17.0.0/24 vrf 1315
```

This rule makes ip packets in vrf `1314` that match `172.17.0.0/24` go to vrf `1315`. Note: the vrf `1315` should have at least one synthetic ip for the receiver to respond to the routed ip packet, otherwise it might not be able to respond.

The hop limit in ip packet will decrease by 1 and it will be dropped if it's 0 before routing.

### 8. route to an address

You may add routes to redirect packets to some address, which is usually expected to be a router.

```
add route to172.17 to vrf 1314 in switch sw0 network 172.17.0.0/24 via 172.16.0.1
```

This rule changes mac address of ip packets in vrf `1314` that match `172.17.0.0/24` to the mac of 172.16.0.1.

The hop limit in ip packet will decrease by 1 and it will be dropped if it's 0 before routing.

### 9. add synthetic ip

Add an ip to a vrf. The ip can respond arp requests or ndp neighbor solicitations, and can respond ICMP/ICMPv6 PING requests. The ip can be used as the default gateway. All route rules in the same vrf will apply to these ips, and when routing to another vrf, the ip in that vrf must exist otherwise arp/ndp requests will not be able to make.

```
add ip 172.16.0.21 to vrf 1314 in switch sw0 mac e2:8b:11:00:00:22
```

You may set the mac more like a synthetic one, e.g. `02:00:00:00:00:01`

### 10. check connected interfaces

```
list-detail iface in switch sw0
```

output example:

```
1) "Iface(remote:sw2,100.64.0.4:18472)"
2) "Iface(user:sw1link,192.168.56.2:45717,lvrf:1314,rvrf:1314)"
```

### 11. check mac and arp table

The mac and arp table are provided together. Data will be filled into the table and empty values will stay empty (e.g. the mac table entry of a mac is missing but in arp table the mac exists, then the output of an arp entry will show no Iface field).

```
list-detail arp in vrf 1314 in sw sw0
```

output example:

```
1) "0a:00:27:00:02:54    172.16.0.254    Iface(remote:sw2,100.64.0.4:18472)                            ARP-TTL:13405    MAC-TTL:299"
2) "1a:a9:6b:b6:a2:2c    172.16.0.3      Iface(user:sw1link,192.168.56.2:45717,lvrf:1314,rvrf:1314)    ARP-TTL:14398    MAC-TTL:299"
3) "52:13:24:51:a3:1e    172.16.0.2      Iface(remote:sw2,100.64.0.4:18472)                            ARP-TTL:14383    MAC-TTL:299"
4) "d6:4b:ba:20:c1:01    172.16.0.4      Iface(user:sw1link,192.168.56.2:45717,lvrf:1314,rvrf:1314)    ARP-TTL:14383    MAC-TTL:299"
5) "fe:73:f8:bb:75:65    172.16.0.1      Iface(remote:sw2,100.64.0.4:18472)                            ARP-TTL:14398    MAC-TTL:299"
```

## Example Topology

It's really simple to configure and to use.

Let's see with the following network:

I'm now using macos and I have a public cloud vm, so I created three virtual machines and connect them to my host:

1. `Host B` 192.168.56.2
2. `Host C` 100.64.0.4
3. `VM` 192.168.56.3
4. `Host X` my public cloud vm (not the real ip in the graph though)

My hosting computer `Host A` runs a switch `sw1`.  
The `Host B` runs a switch `sw2`.  
The `Host C` runs a switch `sw3`.  
The `VM` creates a `vxlan` device and connect to `sw1`.  
The `Host X` runs a switch `sw-pub`.

Connect `sw1`, `sw2`, `sw3` with bare vxlan links as the graph shows. Connect `sw1` to `sw-pub` with two encrypted vxlan links.  
The encrypted link can only be connected to one vrf, so here we need two links.

We create 3 vrfs on all switches, to make it simple, we make the vrf number and the network cidr representation consistent:

* vrf `1`: 172.16.`1`.0/24 + fd00::`1`00/120
* vrf `2`: 172.16.`2`.0/24 + fd00::`2`00/120
* vrf `3`: 172.16.`3`.0/24 + fd00::`3`00/120

When assigning ips to devices, we use the device number as the last 1 bytes of the ip address. So as the ipv6 addresses.  
Note that the ipv6 addresses are not shown in the graph, but they will be configured just like the ipv4 addresses. For example:  
ipv4:`172.16.1.1/24`, ipv6:`fd00::101/120`  
ipv4:`172.16.2.2/24`, ipv6:`fd00::202/120`  
etc...

Add some synthetic(virtual) ips to the switches. Each network need at least one ipv4 and one ipv6 to be used as the default gateway because we will set up routes later. A default gateway allows packets to other networks to flow into the switches.  
Note: to make things easy to remember, use `254`(`fe`) as the last byte of the ip address.

Add the gw ip in vrf 1 on `sw2` as `172.16.1.254`.  
Add the gw ip in vrf 2 on `sw-pub` as `172.16.2.254`.  
Add the gw ip in vrf 3 on `sw3` as `172.16.3.254`.

Also, each switch that has a route rule to another vrf must have a corresponding synthetic ip in that vrf. Just set it to any unused ip would be fine, the values I choose are also recorded in the graph.

Devices and their ips are shown in the graph:

```

                                          +----+
                                          | VM | (192.168.56.3)
                                          +----+
172.16.1.1/24                            172.16.3.6/24                     172.16.1.3/24
      |   172.16.2.2/24                       |                                 |   172.16.2.4/24
  ns1 |        | ns2                          | vrf:3                       ns3 |         | ns4
   +------+ +------+                        +-------+                        +------+ +------+
   | tap1 | | tap2 |                        | vxlan |                        | tap3 | | tap4 |
   +--+---+ +--+---+                        +-------+                        +------+ +------+
      |        |                                |                               |         |
      |        |                                |                               |         |
    vrf:1    vrf:2                              |                             vrf:1     vrf:2
      v        v                (192.168.56.1)  v                               v         v           172.16.3.5/24
    +-------------+                      +-------------+                      +-------------+ vrf:3   +------+
    |             |      vxlan link      |             |      vxlan link      |             |<--------| tap5 |
    | Switch: sw2 |<====================>| Switch: sw1 |<====================>| Switch: sw3 |         +------+
    |             |                      |             |                      |             |<-----+  ns0 iptables MASQ ------> INTERNET
    +-------------+                      +-------------+                      +-------------+ vrf:3 \
      ^  Host B (192.168.56.2)               Host A (100.64.0.1)      (100.64.0.4) Host C         gw:172.16.3.254
vrf:1 |                                        ^ ^                                                ip:172.16.1.193 (vrf:1)
      |                                        | |                                                ip:172.16.2.193 (vrf:2)
 gw:172.16.1.254                               | | 2 encrypted links (for vrf 1->101 and vrf 2->102)
 ip:172.16.2.192 (vrf:2)                       | |
 ip:172.16.3.192 (vrf:3)      (60.205.111.222) v v                              br0
                                     +--------------------+          +---------------------------+
                             vrf:102 |                    | vrf:101  |+--------+                 |
              gw:172.16.2.254 ------>|   Switch: sw-pub   |<----------| tap101 | 172.16.1.101/24 |
    (vrf:101) ip:172.16.1.190        |                    |          |+--------+                 |
                                     +--------------------+          +---------------------------+
                                             Host X



<======> means two switches connected to each other, and packets in all vrf can go through.
<------- means an endpoint device attached to one vrf of the switch, input packets are modified into vxlan packets with configured vrf.
<------> means two switches connected to each other, but packets only in the configured vrfs are allowed.
```

You may use `switch-test-init.sh` to build the testing environment.

### Debug

The bare vxlan endpoint (VM on the graph), and the encrypted link (from sw1 to sw-pub) will not work unless the `CLIENT` side sends at least one vxlan packet first, so if you want the `SERVER` side to communicate with the `CLIENT` side, make a ping request on the `CLIENT` side first.

To check the connectivity between switches, use command `list-detail iface in switch ${switch-name}`.

To check mac+arp table, use command `list-detail arp in vrf ${vrf} in switch ${switch-name}`.

When a packet arrives, switch will record the input interface and the src mac of the input packet.

When an arp request/response or a ndp neighbor solicitation/advertisement arrives, switch will update the arp table.

When an arp entry (or an mac entry whose mac can be fond in arp table) TTL is less than one minute, the switch will send unicast arp/ndp to try to refresh the entry.

When an interface is disconnected, all mac entries of the interface will be removed.

It's possible that a switch reboots and forgets everything, and if the dst endpoint never sends packets, the switch will have to wait for the src side re-send arp broadcast to learn the mac of the dst side. So if you reboot a switch, go to both sides and send a ping request, so that the network can get back to normal state quickly.

### Try it

If everything went ok, you are able to use the script provided earlier to ping all the ips on any endpoint.

This example uses all functionality that the vproxy switch provides.

You may also put all switches on the same host, separate them with port number. However you may need to set launching params `pidFile {}`, `load {}` and `autoSaveFile {}` to avoid file collision between these vproxy instances.

And it is also ok to put all switches into the same vproxy instance. All switches have their own name and can be configured separately (as you can tell from the commands above).

### One script for all above

Run all switches, ns, tap devices, vxlan devices, bridges on one linux host, and try all above functions with one script. All you need is a clean linux virtual machine.

I'm working on it ......
