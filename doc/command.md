# vproxy

## tcp-lb

short version: tl

description: TCP load balancer.

### 1. add

description: Create a loadbalancer.

#### parameter description:

##### 1. acceptorelg

description: Choose an event loop group as the acceptor event loop group. can be the same as worker event loop group.

optional: true

default value: (acceptor-elg)

##### 2. eventloopgroup

description: Choose an event loop group as the worker event loop group. can be the same as acceptor event loop group.

optional: true

default value: (worker-elg)

##### 3. address

description: The bind address of the loadbalancer.

optional: false

default value: null

##### 4. upstream

description: Used as the backend servers.

optional: false

default value: null

##### 5. inbuffersize

description: Input buffer size.

optional: true

default value: 16384 (bytes)

##### 6. outbuffersize

description: Output buffer size.

optional: true

default value: 16384 (bytes)

##### 7. protocol

description: The protocol used by tcp-lb. available options: tcp, http, h2, http/1.x, dubbo, framed-int32, or your customized protocol. See doc for more info.

optional: true

default value: tcp

##### 8. certkey

description: The certificates and keys used by tcp-lb. Multiple cert-key(s) are separated with `,`.

optional: false

default value: null

##### 9. securitygroup

description: Specify a security group for the lb.

optional: true

default value: allow any

examples:

```
$ add tcp-lb lb0 acceptor-elg elg0 event-loop-group elg0 address 127.0.0.1:18080 upstream ups0 in-buffer-size 16384 out-buffer-size 16384
"OK"
```

### 2. list

description: Retrieve names of all tcp-loadbalancers.

examples:

```
$ list tcp-lb
1) "lb0"
```

### 3. listdetail

description: Retrieve detailed info of all tcp-loadbalancers.

examples:

```
$ list-detail tcp-lb
1) "lb0 -> acceptor elg0 worker elg0 bind 127.0.0.1:18080 backend ups0 in-buffer-size 16384 out-buffer-size 16384 protocol tcp security-group secg0"
```

### 4. update

description: Update in-buffer-size or out-buffer-size of an lb.

#### parameter description:

##### 1. inbuffersize

description: Input buffer size.

optional: true

default value: not changed

##### 2. outbuffersize

description: Output buffer size.

optional: true

default value: not changed

##### 3. securitygroup

description: The security group.

optional: true

default value: not changed

examples:

```
$ update tcp-lb lb0 in-buffer-size 32768 out-buffer-size 32768
"OK"
```

### 5. remove

description: Remove and stop a tcp-loadbalancer. The already established connections won't be affected.

examples:

```
$ remove tcp-lb lb0
"OK"
```

## socks5-server

short version: socks5

description: Socks5 proxy server.

### 1. add

description: Create a socks5 server.

#### flag:

##### 1. allownonbackend

description: Allow to access non backend endpoints.

optional: true

default: false

##### 2. denynonbackend

description: Only enable backend endpoints.

optional: true

default: true

#### parameter description:

##### 1. acceptorelg

description: Choose an event loop group as the acceptor event loop group. can be the same as worker event loop group.

optional: true

default value: (acceptor-elg)

##### 2. eventloopgroup

description: Choose an event loop group as the worker event loop group. can be the same as acceptor event loop group.

optional: true

default value: (worker-elg)

##### 3. address

description: The bind address of the loadbalancer.

optional: false

default value: null

##### 4. upstream

description: Used as the backend servers.

optional: false

default value: null

##### 5. inbuffersize

description: Input buffer size.

optional: true

default value: 16384 (bytes)

##### 6. outbuffersize

description: Output buffer size.

optional: true

default value: 16384 (bytes)

##### 7. securitygroup

description: Specify a security group for the socks5 server.

optional: true

default value: allow any

examples:

```
$ add socks5-server s5 acceptor-elg acceptor event-loop-group worker address 127.0.0.1:18081 upstream backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0
"OK"
```

### 2. list

description: Retrieve names of socks5 servers.

examples:

```
$ list socks5-server
1) "s5"
```

### 3. listdetail

description: Retrieve detailed info of socks5 servers.

examples:

```
$ list-detail socks5-server
1) "s5 -> acceptor acceptor worker worker bind 127.0.0.1:18081 backend backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0"
```

### 4. update

description: Update in-buffer-size or out-buffer-size of a socks5 server.

#### flag:

##### 1. allownonbackend

description: Allow to access non backend endpoints.

optional: true

default: false

##### 2. denynonbackend

description: Only enable backend endpoints.

optional: true

default: true

#### parameter description:

##### 1. inbuffersize

description: Input buffer size.

optional: true

default value: not changed

##### 2. outbuffersize

description: Output buffer size.

optional: true

default value: not changed

##### 3. securitygroup

description: The security group.

optional: true

default value: not changed

examples:

```
$ update socks5-server s5 in-buffer-size 8192 out-buffer-size 8192
"OK"
```

### 5. remove

description: Remove a socks5 server.

examples:

```
$ remove socks5-server s5
"OK"
```

## dns-server

short version: dns

description: Dns server.

### 1. add

description: Create a dns server.

#### parameter description:

##### 1. eventloopgroup

description: Choose an event loop group to run the dns server.

optional: true

default value: (worker-elg)

##### 2. address

description: The bind address of the socks5 server.

optional: false

default value: null

##### 3. upstream

description: The domains to be resolved.

optional: false

default value: null

##### 4. ttl

description: The ttl of responded records.

optional: true

default value: 0

##### 5. securitygroup

description: Specify a security group for the dns server.

optional: true

default value: allow any

examples:

```
$ add dns-server dns0 address 127.0.0.1:53 upstream backend-groups ttl 0
"OK"
```

### 2. update

description: Update config of a dns server.

#### parameter description:

##### 1. ttl

description: The ttl of responded records.

optional: true

default value: not changed

##### 2. securitygroup

description: The security group.

optional: true

default value: not changed

examples:

```
$ update dns-server dns0 ttl 60
"OK"
```

### 3. list

description: Retrieve names of dns servers.

examples:

```
$ list dns-server
1) "dns0"
```

### 4. listdetail

description: Retrieve detailed info of dns servers.

examples:

```
$ list-detail dns-server
1) "dns0 -> event-loop-group worker bind 127.0.0.1:53 backend backend-groups security-group (allow-all)"
```

### 5. remove

description: Remove a dns server.

examples:

```
$ remove dns-server dns0
"OK"
```

## event-loop-group

short version: elg

description: A group of event loops.

### 1. add

description: Specify a name and create a event loop group.

examples:

```
$ add event-loop-group elg0
"OK"
```

### 2. list

description: Retrieve names of all event loop groups.

examples:

```
$ list event-loop-group
1) "elg0"
```

```
$ list-detail event-loop-group
1) "elg0"
```

### 3. remove

description: Remove a event loop group.

examples:

```
$ remove event-loop-group elg0
"OK"
```

## upstream

short version: ups

description: A resource containing multiple `server-group` resources.

### 1. add

description: Specify a name and create an upstream resource.

examples:

```
$ add upstream ups0
"OK"
```

### 2. list

description: Retrieve names of all upstream resources.

examples:

```
$ list upstream
1) "ups0"
```

```
$ list-detail upstream
1) "ups0"
```

### 3. remove

description: Remove an upstream resource.

examples:

```
$ remove upstream ups0
"OK"
```

## server-group

short version: sg

description: A group of remote servers, which will run health check for all contained servers.

### 1. add

description: Specify name, event loop, load balancing method, health check config and create a server group.

#### parameter description:

##### 1. timeout

description: Health check connect timeout (ms).

optional: false

default value: null

##### 2. period

description: Do check every `${period}` milliseconds.

optional: false

default value: null

##### 3. up

description: Set server status to UP after succeeded for `${up}` times.

optional: false

default value: null

##### 4. down

description: Set server status to DOWN after failed for `${down}` times.

optional: false

default value: null

##### 5. protocol

description: The protocol used for checking the servers, you may choose `tcp`, `none`.

optional: true

default value: tcp

##### 6. method

description: Loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`.

optional: true

default value: wrr

##### 7. annotations

description: Extra info for the server-group, such as host info, health check url. Must be a json and values must be strings.

optional: true

default value: {}

##### 8. eventloopgroup

description: Choose a event-loop-group for the server group. health check operations will be performed on the event loop group.

optional: true

default value: (control-elg)

examples:

```
$ add server-group sg0 timeout 500 period 800 up 4 down 5 method wrr elg elg0
"OK"
```

### 2. addto

description: Attach an existing server group into an `upstream` resource.

#### parameter description:

##### 1. weight

description: The weight of group in this upstream resource.

optional: true

default value: 10

##### 2. annotations

description: Extra info for the server-group inside upstream, such as host info. Must be a json and values must be strings.

optional: true

default value: {}

examples:

```
$ add server-group sg0 to upstream ups0 weight 10
"OK"
```

### 3. list

description: Retrieve names of all server group (s) on top level or in an upstream.

examples:

```
$ list server-group
1) "sg0"
```

```
$ list server-group in upstream ups0
1) "sg0"
```

### 4. listdetail

description: Retrieve detailed info of all server group(s).

examples:

```
$ list-detail server-group
1) "sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 annotations {}"
```

```
$ list-detail server-group in upstream ups0
1) "sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 annotations {} weight 10"
```

### 5. update

description: Change health check config or load balancing algorithm.

Param list is the same as add, but not all required.

Also you can change the weight of a group in an upstream resource.

#### parameter description:

##### 1. timeout

description: Health check connect timeout (ms).

optional: true

default value: not changed

##### 2. period

description: Do check every `${period}` milliseconds.

optional: true

default value: not changed

##### 3. up

description: Set server status to UP after succeeded for `${up}` times.

optional: true

default value: not changed

##### 4. down

description: Set server status to DOWN after failed for `${down}` times.

optional: true

default value: not changed

##### 5. protocol

description: The protocol used for checking the servers, you may choose `tcp`, `none`. Note: this field will be set to `tcp` as default when updating other hc options.

optional: true

default value: not changed

##### 6. method

description: Loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`.

optional: true

default value: not changed

##### 7. weight

description: The weight of group in the upstream resource (only available for server-group in upstream).

optional: true

default value: not changed

##### 8. annotations

description: Annotation of the group itself, or the group in the upstream.

optional: true

default value: not changed

examples:

```
$ update server-group sg0 timeout 500 period 600 up 3 down 2
"OK"
```

```
$ update server-group sg0 method wlc
"OK"
```

```
$ update server-group sg0 in upstream ups0 weight 5
"OK"
```

### 6. remove

description: Remove a server group.

examples:

```
$ remove server-group sg0
"OK"
```

### 7. removefrom

description: Detach the group from an `upstream` resource.

examples:

```
$ remove server-group sg0 from upstream ups0
"OK"
```

## event-loop

short version: el

description: Event loop.

### 1. addto

description: Specify a name, a event loop group, and create a new event loop in the specified group.

examples:

```
$ add event-loop el0 to elg elg0
"OK"
```

### 2. list

description: Retrieve names of all event loops in a event loop group.

examples:

```
$ list event-loop in event-loop-group elg0
1) "el0"
```

```
$ list-detail event-loop in event-loop-group elg0
1) "el0"
```

### 3. removefrom

description: Remove a event loop from event loop group.

examples:

```
$ remove event-loop el0 from event-loop-group elg0
"OK"
```

## server

short version: svr

description: A remote endpoint.

### 1. addto

description: Specify name, remote ip:port, weight, and attach the server into the server group.

#### parameter description:

##### 1. address

description: Remote address, ip:port.

optional: false

default value: null

##### 2. weight

description: Weight of the server, which will be used by wrr, wlc and source algorithm.

optional: true

default value: 10

examples:

```
$ add server svr0 to server-group sg0 address 127.0.0.1:6379 weight 10
"OK"
```

### 2. list

description: Retrieve names of all servers in a server group.

examples:

```
$ list server in server-group sg0
1) "svr0"
```

### 3. listdetail

description: Retrieve detailed info of all servers in a server group.

examples:

```
$ list-detail server in server-group sg0
1) "svr0 -> connect-to 127.0.0.1:6379 weight 10 currently DOWN"
```

### 4. update

description: Change weight of the server.

examples:

```
$ update server svr0 in server-group sg0 weight 11
"OK"
```

### 5. removefrom

description: Remove a server from a server group.

examples:

```
$ remove server svr0 from server-group sg0
"OK"
```

## security-group

short version: secg

description: A white/black list, see `security-group-rule` for more info.

### 1. add

description: Create a security group.

#### parameter description:

##### 1. dft

description: Default: enum {allow, deny}
if set to allow, then will allow connection if all rules not match
if set to deny, then will deny connection if all rules not match.

optional: false

default value: null

examples:

```
$ add security-group secg0 default allow
"OK"
```

### 2. list

description: Retrieve names of all security groups.

examples:

```
$ list security-group
1) "secg0"
```

### 3. listdetail

description: Retrieve detailed info of all security groups.

examples:

```
$ list-detail security-group
1) "secg0 -> default allow"
```

### 4. update

description: Update properties of a security group.

#### parameter description:

##### 1. dft

description: Default: enum {allow, deny}.

optional: false

default value: null

examples:

```
$ update security-group secg0 default deny
"OK"
```

### 5. remove

description: Remove a security group.

examples:

```
$ remove security-group secg0
"OK"
```

## security-group-rule

short version: secgr

description: A rule containing protocol, source network, dest port range and whether to deny.

### 1. add

description: Create a rule in the security group.

#### parameter description:

##### 1. network

description: A cidr string for checking client ip.

optional: false

default value: null

##### 2. protocol

description: Enum {TCP, UDP}.

optional: false

default value: null

##### 3. portrange

description: A tuple of integer for vproxy port, 0 <= first <= second <= 65535.

optional: false

default value: null

##### 4. dft

description: Enum {allow, deny}
if set to allow, then will allow the connection if matches
if set to deny, then will deny the connection if matches.

optional: false

default value: null

examples:

```
$ add security-group-rule secgr0 to security-group secg0 network 10.127.0.0/16 protocol TCP port-range 22,22 default allow
"OK"
```

### 2. list

description: Retrieve names of all rules in a security group.

examples:

```
$ list security-group-rule in security-group secg0
1) "secgr0"
```

### 3. listdetail

description: Retrieve detailed info of all rules in a security group.

examples:

```
$ list-detail security-group-rule in security-group secg0
1) "secgr0 -> allow 10.127.0.0/16 protocol TCP port [22,33]"
```

### 4. remove

description: Remove a rule from a security group.

examples:

```
$ remove security-group-rule secgr0 from security-group secg0
"OK"
```

## cert-key

short version: ck

description: Some certificates and one key.

### 1. add

description: Load certificates and key from file.

#### parameter description:

##### 1. cert

description: The cert file path. Multiple files are separated with `,`.

optional: false

default value: null

##### 2. key

description: The key file path.

optional: false

default value: null

examples:

```
$ add cert-key vproxy.cassite.net cert ~/cert.pem key ~/key.pem
"OK"
```

### 2. list

description: View loaded cert-key resources.

examples:

```
$ list cert-key
1) "vproxy.cassite.net"
```

### 3. remove

description: Remove a cert-key resource.

examples:

```
$ remove cert-key vproxy.cassite.net
"OK"
```

## dns-cache

description: The dns record cache. It's a host -> ipv4List, ipv6List map. It can only be accessed from the (default) dns resolver.

### 1. list

description: Count current cache.

examples:

```
$ list dns-cache in resolver (default)
(integer) 1
```

### 2. listdetail

description: List detailed info of dns cache.

The return values are:

host.
ipv4 ip list.
ipv6 ip list.

examples:

```
$ list-detail dns-cache in resolver (default)
1) 1) "localhost"
   2) 1) "127.0.0.1"
   3) 1) "[0000:0000:0000:0000:0000:0000:0000:0001]"
```

### 3. forceremove

description: Specify the host and remove the dns cache.

examples:

```
$ force-remove dns-cache localhost from resolver (default)
"OK"
```

## server-sock

short version: ss

description: Represents a `ServerSocketChannel`, which binds an ip:port.

### 1. list

description: Count server-socks.

examples:

```
$ list server-sock in el el0 in elg elg0
(integer) 1
```

```
$ list server-sock in tcp-lb lb0
(integer) 1
```

```
$ list server-sock in socks5-server s5
(integer) 1
```

### 2. listdetail

description: Get info about bind servers.

examples:

```
$ list-detail server-sock in el el0 in elg elg0
1) "127.0.0.1:6380"
```

```
$ list-detail server-sock in tcp-lb lb0
1) "127.0.0.1:6380"
```

```
$ list-detail server-sock in socks5-server s5
1) "127.0.0.1:18081"
```

## connection

short version: conn

description: Represents a `SocketChannel`.

### 1. list

description: Count connections.

examples:

```
$ list connection in el el0 in elg elg0
(integer) 2
```

```
$ list connection in tcp-lb lb0
(integer) 2
```

```
$ list connection in socks5-server s5
(integer) 2
```

```
$ list connection in server svr0 in sg sg0
(integer) 1
```

### 2. listdetail

description: Get info about connections.

examples:

```
$ list-detail connection in el el0 in elg elg0
1) "127.0.0.1:63537/127.0.0.1:6379"
2) "127.0.0.1:63536/127.0.0.1:6380"
```

```
$ list-detail connection in tcp-lb lb0
1) "127.0.0.1:63536/127.0.0.1:6380"
2) "127.0.0.1:63537/127.0.0.1:6379"
```

```
$ list-detail connection in socks5-server s5
1) "127.0.0.1:55981/127.0.0.1:18081"
2) "127.0.0.1:55982/127.0.0.1:16666"
```

```
$ list-detail connection in server svr0 in sg sg0
1) "127.0.0.1:63537/127.0.0.1:6379"
```

### 3. forceremove

description: Close the connection, and if the connection is bond to a session, the session will be closed as well.

Supports regexp pattern or plain string:

* if the input starts with `/` and ends with `/`, then it's considered as a regexp.
* otherwise it matches the full string.

examples:

```
$ force-remove conn 127.0.0.1:57629/127.0.0.1:16666 from el worker2 in elg worker
"OK"
```

```
$ force-remove conn /.*/ from el worker2 in elg worker
"OK"
```

## session

short version: sess

description: Represents a tuple of connections: the connection from client to lb, and the connection from lb to backend server.

### 1. list

description: Count loadbalancer sessions.

examples:

```
$ list session in tcp-lb lb0
(integer) 1
```

```
$ list session in socks5-server s5
(integer) 2
```

### 2. listdetail

description: Get info about loadbalancer sessions.

examples:

```
$ list-detail session in tcp-lb lb0
1) 1) "127.0.0.1:63536/127.0.0.1:6380"
   2) "127.0.0.1:63537/127.0.0.1:6379"
```

```
$ list-detail session in socks5-server s5
1) 1) "127.0.0.1:53589/127.0.0.1:18081"
   2) "127.0.0.1:53591/127.0.0.1:16666"
2) 1) "127.0.0.1:53590/127.0.0.1:18081"
   2) "127.0.0.1:53592/127.0.0.1:16666"
```

### 3. forceremove

description: Close a session from lb.

examples:

```
$ force-remove sess 127.0.0.1:58713/127.0.0.1:18080->127.0.0.1:58714/127.0.0.1:16666 from tl lb0
"OK"
```

```
$ force-remove sess /127.0.0.1:58713.*/ from tl lb0
"OK"
```

## bytes-in

short version: bin

description: Statistics: bytes flow from remote to local.

### 1. list

description: Get history total input bytes from a resource.

examples:

```
$ list bytes-in in server-sock 127.0.0.1:6380 in tl lb0
(integer) 45
```

```
$ list bytes-in in connection 127.0.0.1:63536/127.0.0.1:6380 in el el0 in elg elg0
(integer) 45
```

```
$ list bytes-in in server svr0 in sg sg0
(integer) 9767
```

## bytes-out

short version: bout

description: Statistics: bytes flow from local to remote.

### 1. list

description: Get history total output bytes from a resource.

examples:

```
$ list bytes-out in server-sock 127.0.0.1:6380 in tl lb0
(integer) 9767
```

```
$ list bytes-out in connection 127.0.0.1:63536/127.0.0.1:6380 in el el0 in elg elg0
(integer) 9767
```

```
$ list bytes-out in server svr0 in sg sg0
(integer) 45
```

## accepted-conn-count

description: Statistics: successfully accpeted connections.

### 1. list

description: Get history total accepted connection count.

examples:

```
$ list accepted-conn-count in server-sock 127.0.0.1:6380 in tl lb0
(integer) 2
```

## switch

short version: sw

description: A switch for vproxy wrapped vxlan packets.

### 1. add

description: Create a switch.

#### parameter description:

##### 1. address

description: Binding udp address of the switch for wrapped vxlan packets.

optional: false

default value: null

##### 2. mactabletimeout

description: Timeout for mac table (ms).

optional: true

default value: 300000

##### 3. arptabletimeout

description: Timeout for arp table (ms).

optional: true

default value: 14400000

##### 4. eventloopgroup

description: The event loop group used for handling packets.

optional: true

default value: (worker-elg)

##### 5. securitygroup

description: The security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected).

optional: true

default value: (allow-all)

##### 6. mtu

description: Default mtu setting for new connected ports.

optional: true

default value: 1500

##### 7. flood

description: Default flood setting for new connected ports.

optional: true

default value: allow

examples:

```
$ add switch sw0 address 0.0.0.0:4789
"OK"
```

### 2. update

description: Update a switch.

#### parameter description:

##### 1. mactabletimeout

description: Timeout for mac table (ms).

optional: true

default value: not changed

##### 2. arptabletimeout

description: Timeout for arp table (ms).

optional: true

default value: not changed

##### 3. securitygroup

description: The security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected).

optional: true

default value: not changed

##### 4. mtu

description: Default mtu setting for new connected ports, updating it will not affect the existing ones.

optional: true

default value: not changed

##### 5. flood

description: Default flood setting for new connected ports, updating it will not affect the existing ones.

optional: true

default value: not changed

examples:

```
$ update switch sw0 mac-table-timeout 60000 arp-table-timeout 120000
"OK"
```

### 3. list

description: Get names of switches.

examples:

```
$ list switch
1) "sw0"
```

### 4. listdetail

description: Get detailed info of switches.

examples:

```
$ list-detail switch
1) "sw0" -> event-loop-group worker bind 0.0.0.0:4789 password p@sSw0rD mac-table-timeout 300000 arp-table-timeout 14400000 bare-vxlan-access (allow-all)
```

### 5. remove

description: Stop and remove a switch.

examples:

```
$ remove switch sw0
"OK"
```

### 6. addto

description: Add a remote switch ref to a local switch. note: use list iface to see these remote switches.

#### flag:

##### 1. noswitchflag

description: Do not add switch flag on vxlan packets sent through this iface.

optional: true

default: false

#### parameter description:

##### 1. address

description: The remote switch address.

optional: false

default value: null

examples:

```
$ add switch sw1 to switch sw0 address 100.64.0.1:18472
"OK"
```

### 7. removefrom

description: Remove a remote switch ref from a local switch.

examples:

```
$ remove switch sw1 from switch sw0
"OK"
```

## vpc

description: A private network.

### 1. list

description: List existing vpcs in a switch.

examples:

```
$ list vpc in switch sw0
1) (integer) 1314
```

### 2. listdetail

description: List detailed info about vpcs in a switch.

examples:

```
$ list-detail vpc in switch sw0
1) "1314 -> v4network 172.16.0.0/16"
```

### 3. addto

description: Create a vpc in a switch. the name should be vni of the vpc.

#### parameter description:

##### 1. v4network

description: The ipv4 network allowed in this vpc.

optional: false

default value: null

##### 2. v6network

description: The ipv6 network allowed in this vpc.

optional: true

default value: not allowed

examples:

```
$ add vpc 1314 to switch sw0 v4network 172.16.0.0/16
"OK"
```

### 4. removefrom

description: Remove a vpc from a switch.

examples:

```
$ remote vpc 1314 from switch sw0
"OK"
```

## iface

description: Connected interfaces.

### 1. list

description: Count currently connected interfaces in a switch.

examples:

```
$ list iface in switch sw0
(integer) 2
```

### 2. listdetail

description: List current connected interfaces in a switch.

examples:

```
$ list-detail iface in switch sw0
1) "Iface(192.168.56.2:8472)"
2) "Iface(100.64.0.4:8472)"
```

### 3. update

description: Update interface config.

#### parameter description:

##### 1. mtu

description: Mtu of this interface.

optional: true

default value: 1500

##### 2. flood

description: Whether to allow flooding traffic through this interface, allow or deny.

optional: true

default value: allow

examples:

```
$ update iface tap:tap0 in switch sw0 mtu 9000 flood allow
"OK"
```

```
$ update iface remote:sw-x in switch sw0 mtu 1500 flood deny
"OK"
```

```
$ update iface ucli:hello in switch sw0 mtu 1500 flood deny
"OK"
```

```
$ update iface user:hello in switch sw0 mtu 1500 flood allow
"OK"
```

```
$ update iface 10.0.0.1:8472 in switch sw0 mtu 1500 flood allow
"OK"
```

## arp

description: Arp and mac table entries.

### 1. list

description: Count entries in a vpc.

examples:

```
$ list arp in vpc 1314 in switch sw0
(integer) 2
```

### 2. listdetail

description: List arp and mac table entries in a vpc.

examples:

```
$ list-detail arp in vpc 1314 in switch sw0
1) "aa:92:96:2f:3b:7d        10.213.0.1             Iface(127.0.0.1:54042)        ARP-TTL:14390        MAC-TTL:299"
2) "fa:e8:aa:6c:45:f4        10.213.0.2             Iface(127.0.0.1:57374)        ARP-TTL:14390        MAC-TTL:299"
```

## user

description: User in a switch.

### 1. list

description: List user names in a switch.

examples:

```
$ list user in switch sw0
1) "hello"
```

### 2. listdetail

description: List all user info in a switch.

examples:

```
$ list-detail user in switch sw0
1) "hello" -> vni 1314
```

### 3. add

description: Add a user to a switch.

#### parameter description:

##### 1. pass

description: Password of the user.

optional: false

default value: null

##### 2. vni

description: Vni assigned for the user.

optional: false

default value: null

##### 3. mtu

description: Mtu for the user interface when the user is connected.

optional: true

default value: mtu setting of the switch

##### 4. flood

description: Whether the user interface allows flooding traffic.

optional: true

default value: flood setting of the switch

examples:

```
$ add user hello to switch sw0 vni 1314 password p@sSw0rD
"OK"
```

### 4. update

description: Update user info in a switch.

#### parameter description:

##### 1. mtu

description: Mtu for the user interface when the user is connected, updating it will not affect connected ones.

optional: true

default value: not changed

##### 2. flood

description: Whether the user interface allows flooding traffic, updating it will not affect connected ones.

optional: true

default value: not changed

examples:

```
$ update user hello in switch sw0 mtu 1500 flood allow
"OK"
```

### 5. remove

description: Remove a user from a switch.

examples:

```
$ remove user hello from switch sw0
"OK"
```

## tap

description: Add/remove a tap device and bind/detach it to/from a switch. The input alias may also be a pattern, see linux tuntap manual. Note: 1) use list iface to see these tap devices, 2) should set -Dvfd=posix or -Dvfd=windows.

### 1. addto

description: Add a user to a switch. Note: the result string is the name of the tap device because might be generated.

#### parameter description:

##### 1. vni

description: Vni of the vpc which the tap device is attached to.

optional: false

default value: null

##### 2. postscript

description: Post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch).

optional: true

default value: (empty)

##### 3. mtu

description: Mtu of this tap device.

optional: true

default value: mtu setting of the switch

##### 4. flood

description: Whether the tap device allows flooding traffic.

optional: true

default value: flood setting of the switch

examples:

```
$ add tap tap%d to switch sw0 vni 1314
"tap0"
```

### 2. removefrom

description: Remove and close a tap from a switch.

examples:

```
$ remove tap tap0 from switch sw0
"OK"
```

## tun

description: Add/remove a tun device and bind/detach it to/from a switch. The input alias may also be a pattern, see linux tuntap manual. Note: 1) use list iface to see these tun devices, 2) should set -Dvfd=posix.

### 1. addto

description: Add a user to a switch. Note: the result string is the name of the tun device because might be generated.

#### parameter description:

##### 1. vni

description: Vni of the vpc which the tun device is attached to.

optional: false

default value: null

##### 2. mac

description: Mac address of this tun device. the switch requires l2 layer frames for handling packets.

optional: false

default value: null

##### 3. postscript

description: Post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch).

optional: true

default value: (empty)

##### 4. mtu

description: Mtu of this tun device.

optional: true

default value: mtu setting of the switch

##### 5. flood

description: Whether the tun device allows flooding traffic.

optional: true

default value: flood setting of the switch

examples:

```
$ add tun tun%d to switch sw0 vni 1314
"tun0"
```

```
$ add tun utun9 to switch sw0 vni 1314
"utun9"
```

### 2. removefrom

description: Remove and close a tun from a switch.

examples:

```
$ remove tun tun0 from switch sw0
"OK"
```

## user-client

short version: ucli

description: User client of an encrypted tunnel to remote switch. Note: use list iface to see these clients.

### 1. add

description: Add a user client to a switch.

#### parameter description:

##### 1. pass

description: Password of the user.

optional: false

default value: null

##### 2. vni

description: Vni which the user is assigned to.

optional: false

default value: null

##### 3. address

description: Remote switch address to connect to.

optional: false

default value: null

examples:

```
$ add user-client hello to switch sw0 password p@sSw0rD vni 1314 address 192.168.77.1:18472
"OK"
```

### 2. remove

description: Remove a user client from a switch.

#### parameter description:

##### 1. address

description: Remote switch address the client connected to.

optional: false

default value: null

examples:

```
$ remove user-client hello from switch sw0 address 192.168.77.1:18472
"OK"
```

## ip

description: Synthetic ip in a vpc of a switch.

### 1. list

description: Show synthetic ips in a vpc of a switch.

examples:

```
$ list ip in vpc 1314 in switch sw0
1) "172.16.0.21"
2) "[2001:0db8:0000:f101:0000:0000:0000:0002]"
```

### 2. listdetail

description: Show detailed info about synthetic ips in a vpc of a switch.

examples:

```
$ list-detail ip in vpc 1314 in switch sw0
1) "172.16.0.21 -> mac e2:8b:11:00:00:22"
2) "[2001:0db8:0000:f101:0000:0000:0000:0002] -> mac e2:8b:11:00:00:33"
```

### 3. addto

description: Add a synthetic ip to a vpc of a switch.

#### parameter description:

##### 1. mac

description: Mac address that the ip assigned on.

optional: false

default value: null

examples:

```
$ add ip 172.16.0.21 to vpc 1314 in switch sw0 mac e2:8b:11:00:00:22
"OK"
```

### 4. removefrom

description: Remove a synthetic ip from a vpc of a switch.

examples:

```
$ remove ip 172.16.0.21 from vpc 1314 in switch sw0
"OK"
```

## route

description: Route rules in a vpc of a switch.

### 1. list

description: Show route rule names in a vpc of a switch.

examples:

```
$ list route in vpc 1314 in switch sw0
1) "to172.17"
2) "to2001:0db8:0000:f102"
```

### 2. listdetail

description: Show detailed info about route rules in a vpc of a switch.

examples:

```
$ list-detail route in vpc 1314 in switch sw0
1) "to172.17 -> network 172.17.0.0/24 vni 1315"
2) "to2001:0db8:0000:f102 -> network [2001:0db8:0000:f102:0000:0000:0000:0000]/64 vni 1315"
```

### 3. addto

description: Add a route to a vpc of a switch.

#### parameter description:

##### 1. network

description: Network to be matched.

optional: false

default value: null

##### 2. vni

description: The vni to send packet to. only one of vni|via can be used.

optional: false

default value: null

##### 3. via

description: The address to forward the packet to. only one of via|vni can be used.

optional: false

default value: null

examples:

```
$ add route to172.17 to vpc 1314 in switch sw0 network 172.17.0.0/24 vni 1315
"OK"
```

```
$ add route to172.17 to vpc 1314 in switch sw0 network 172.17.0.0/24 via 172.16.0.1
"OK"
```

### 4. removefrom

description: Remove a route rule from a vpc of a switch.

examples:

```
$ remove route to172.17 from vpc 1314 in switch sw0
"OK"
```
