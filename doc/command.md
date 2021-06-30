# vproxy

## 1. tcp-lb

short version: tl

description: TCP load balancer.

actions:

### 1) add

description: Create a loadbalancer.

parameters:

1. `acceptorelg`. [optional] [default value: (acceptor-elg)] Choose an event loop group as the acceptor event loop group. can be the same as worker event loop group.

2. `eventloopgroup`. [optional] [default value: (worker-elg)] Choose an event loop group as the worker event loop group. can be the same as acceptor event loop group.

3. `address`. The bind address of the loadbalancer.

4. `upstream`. Used as the backend servers.

5. `inbuffersize`. [optional] [default value: 16384 (bytes)] Input buffer size.

6. `outbuffersize`. [optional] [default value: 16384 (bytes)] Output buffer size.

7. `protocol`. [optional] [default value: tcp] The protocol used by tcp-lb. available options: tcp, http, h2, http/1.x, dubbo, framed-int32, or your customized protocol. See doc for more info.

8. `certkey`. The certificates and keys used by tcp-lb. Multiple cert-key(s) are separated with `,`.

9. `securitygroup`. [optional] [default value: allow any] Specify a security group for the lb.

examples:

```
$ add tcp-lb lb0 acceptor-elg elg0 event-loop-group elg0 address 127.0.0.1:18080 upstream ups0 in-buffer-size 16384 out-buffer-size 16384
"OK"
```

### 2) list

description: Retrieve names of all tcp-loadbalancers.

examples:

```
$ list tcp-lb
1) "lb0"
```

### 3) listdetail

description: Retrieve detailed info of all tcp-loadbalancers.

examples:

```
$ list-detail tcp-lb
1) "lb0 -> acceptor elg0 worker elg0 bind 127.0.0.1:18080 backend ups0 in-buffer-size 16384 out-buffer-size 16384 protocol tcp security-group secg0"
```

### 4) update

description: Update in-buffer-size or out-buffer-size of an lb.

parameters:

1. `inbuffersize`. [optional] [default value: not changed] Input buffer size.

2. `outbuffersize`. [optional] [default value: not changed] Output buffer size.

3. `securitygroup`. [optional] [default value: not changed] The security group.

examples:

```
$ update tcp-lb lb0 in-buffer-size 32768 out-buffer-size 32768
"OK"
```

### 5) remove

description: Remove and stop a tcp-loadbalancer. The already established connections won't be affected.

examples:

```
$ remove tcp-lb lb0
"OK"
```

## 2. socks5-server

short version: socks5

description: Socks5 proxy server.

actions:

### 1) add

description: Create a socks5 server.

flags:

1. `allownonbackend`. [optional] Allow to access non backend endpoints.

2. `denynonbackend`. [optional] [is default] Only enable backend endpoints.

parameters:

1. `acceptorelg`. [optional] [default value: (acceptor-elg)] Choose an event loop group as the acceptor event loop group. can be the same as worker event loop group.

2. `eventloopgroup`. [optional] [default value: (worker-elg)] Choose an event loop group as the worker event loop group. can be the same as acceptor event loop group.

3. `address`. The bind address of the loadbalancer.

4. `upstream`. Used as the backend servers.

5. `inbuffersize`. [optional] [default value: 16384 (bytes)] Input buffer size.

6. `outbuffersize`. [optional] [default value: 16384 (bytes)] Output buffer size.

7. `securitygroup`. [optional] [default value: allow any] Specify a security group for the socks5 server.

examples:

```
$ add socks5-server s5 acceptor-elg acceptor event-loop-group worker address 127.0.0.1:18081 upstream backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0
"OK"
```

### 2) list

description: Retrieve names of socks5 servers.

examples:

```
$ list socks5-server
1) "s5"
```

### 3) listdetail

description: Retrieve detailed info of socks5 servers.

examples:

```
$ list-detail socks5-server
1) "s5 -> acceptor acceptor worker worker bind 127.0.0.1:18081 backend backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0"
```

### 4) update

description: Update in-buffer-size or out-buffer-size of a socks5 server.

flags:

1. `allownonbackend`. [optional] Allow to access non backend endpoints.

2. `denynonbackend`. [optional] [is default] Only enable backend endpoints.

parameters:

1. `inbuffersize`. [optional] [default value: not changed] Input buffer size.

2. `outbuffersize`. [optional] [default value: not changed] Output buffer size.

3. `securitygroup`. [optional] [default value: not changed] The security group.

examples:

```
$ update socks5-server s5 in-buffer-size 8192 out-buffer-size 8192
"OK"
```

### 5) remove

description: Remove a socks5 server.

examples:

```
$ remove socks5-server s5
"OK"
```

## 3. dns-server

short version: dns

description: Dns server.

actions:

### 1) add

description: Create a dns server.

parameters:

1. `eventloopgroup`. [optional] [default value: (worker-elg)] Choose an event loop group to run the dns server.

2. `address`. The bind address of the socks5 server.

3. `upstream`. The domains to be resolved.

4. `ttl`. [optional] [default value: 0] The ttl of responded records.

5. `securitygroup`. [optional] [default value: allow any] Specify a security group for the dns server.

examples:

```
$ add dns-server dns0 address 127.0.0.1:53 upstream backend-groups ttl 0
"OK"
```

### 2) update

description: Update config of a dns server.

parameters:

1. `ttl`. [optional] [default value: not changed] The ttl of responded records.

2. `securitygroup`. [optional] [default value: not changed] The security group.

examples:

```
$ update dns-server dns0 ttl 60
"OK"
```

### 3) list

description: Retrieve names of dns servers.

examples:

```
$ list dns-server
1) "dns0"
```

### 4) listdetail

description: Retrieve detailed info of dns servers.

examples:

```
$ list-detail dns-server
1) "dns0 -> event-loop-group worker bind 127.0.0.1:53 backend backend-groups security-group (allow-all)"
```

### 5) remove

description: Remove a dns server.

examples:

```
$ remove dns-server dns0
"OK"
```

## 4. event-loop-group

short version: elg

description: A group of event loops.

actions:

### 1) add

description: Specify a name and create a event loop group.

examples:

```
$ add event-loop-group elg0
"OK"
```

### 2) list

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

### 3) remove

description: Remove a event loop group.

examples:

```
$ remove event-loop-group elg0
"OK"
```

## 5. upstream

short version: ups

description: A resource containing multiple `server-group` resources.

actions:

### 1) add

description: Specify a name and create an upstream resource.

examples:

```
$ add upstream ups0
"OK"
```

### 2) list

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

### 3) remove

description: Remove an upstream resource.

examples:

```
$ remove upstream ups0
"OK"
```

## 6. server-group

short version: sg

description: A group of remote servers, which will run health check for all contained servers.

actions:

### 1) add

description: Specify name, event loop, load balancing method, health check config and create a server group.

parameters:

1. `timeout`. Health check connect timeout (ms).

2. `period`. Do check every `${period}` milliseconds.

3. `up`. Set server status to UP after succeeded for `${up}` times.

4. `down`. Set server status to DOWN after failed for `${down}` times.

5. `protocol`. [optional] [default value: tcp] The protocol used for checking the servers, you may choose `tcp`, `none`.

6. `method`. [optional] [default value: wrr] Loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`.

7. `annotations`. [optional] [default value: {}] Extra info for the server-group, such as host info, health check url. Must be a json and values must be strings.

8. `eventloopgroup`. [optional] [default value: (control-elg)] Choose a event-loop-group for the server group. health check operations will be performed on the event loop group.

examples:

```
$ add server-group sg0 timeout 500 period 800 up 4 down 5 method wrr elg elg0
"OK"
```

### 2) addto

description: Attach an existing server group into an `upstream` resource.

parameters:

1. `weight`. [optional] [default value: 10] The weight of group in this upstream resource.

2. `annotations`. [optional] [default value: {}] Extra info for the server-group inside upstream, such as host info. Must be a json and values must be strings.

examples:

```
$ add server-group sg0 to upstream ups0 weight 10
"OK"
```

### 3) list

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

### 4) listdetail

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

### 5) update

description: Change health check config or load balancing algorithm.

Param list is the same as add, but not all required.

Also you can change the weight of a group in an upstream resource.

parameters:

1. `timeout`. [optional] [default value: not changed] Health check connect timeout (ms).

2. `period`. [optional] [default value: not changed] Do check every `${period}` milliseconds.

3. `up`. [optional] [default value: not changed] Set server status to UP after succeeded for `${up}` times.

4. `down`. [optional] [default value: not changed] Set server status to DOWN after failed for `${down}` times.

5. `protocol`. [optional] [default value: not changed] The protocol used for checking the servers, you may choose `tcp`, `none`. Note: this field will be set to `tcp` as default when updating other hc options.

6. `method`. [optional] [default value: not changed] Loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`.

7. `weight`. [optional] [default value: not changed] The weight of group in the upstream resource (only available for server-group in upstream).

8. `annotations`. [optional] [default value: not changed] Annotation of the group itself, or the group in the upstream.

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

### 6) remove

description: Remove a server group.

examples:

```
$ remove server-group sg0
"OK"
```

### 7) removefrom

description: Detach the group from an `upstream` resource.

examples:

```
$ remove server-group sg0 from upstream ups0
"OK"
```

## 7. event-loop

short version: el

description: Event loop.

actions:

### 1) addto

description: Specify a name, a event loop group, and create a new event loop in the specified group.

examples:

```
$ add event-loop el0 to elg elg0
"OK"
```

### 2) list

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

### 3) removefrom

description: Remove a event loop from event loop group.

examples:

```
$ remove event-loop el0 from event-loop-group elg0
"OK"
```

## 8. server

short version: svr

description: A remote endpoint.

actions:

### 1) addto

description: Specify name, remote ip:port, weight, and attach the server into the server group.

parameters:

1. `address`. Remote address, ip:port.

2. `weight`. [optional] [default value: 10] Weight of the server, which will be used by wrr, wlc and source algorithm.

examples:

```
$ add server svr0 to server-group sg0 address 127.0.0.1:6379 weight 10
"OK"
```

### 2) list

description: Retrieve names of all servers in a server group.

examples:

```
$ list server in server-group sg0
1) "svr0"
```

### 3) listdetail

description: Retrieve detailed info of all servers in a server group.

examples:

```
$ list-detail server in server-group sg0
1) "svr0 -> connect-to 127.0.0.1:6379 weight 10 currently DOWN"
```

### 4) update

description: Change weight of the server.

examples:

```
$ update server svr0 in server-group sg0 weight 11
"OK"
```

### 5) removefrom

description: Remove a server from a server group.

examples:

```
$ remove server svr0 from server-group sg0
"OK"
```

## 9. security-group

short version: secg

description: A white/black list, see `security-group-rule` for more info.

actions:

### 1) add

description: Create a security group.

parameters:

1. `dft`. Default: enum {allow, deny}
if set to allow, then will allow connection if all rules not match
if set to deny, then will deny connection if all rules not match.

examples:

```
$ add security-group secg0 default allow
"OK"
```

### 2) list

description: Retrieve names of all security groups.

examples:

```
$ list security-group
1) "secg0"
```

### 3) listdetail

description: Retrieve detailed info of all security groups.

examples:

```
$ list-detail security-group
1) "secg0 -> default allow"
```

### 4) update

description: Update properties of a security group.

parameters:

1. `dft`. Default: enum {allow, deny}.

examples:

```
$ update security-group secg0 default deny
"OK"
```

### 5) remove

description: Remove a security group.

examples:

```
$ remove security-group secg0
"OK"
```

## 10. security-group-rule

short version: secgr

description: A rule containing protocol, source network, dest port range and whether to deny.

actions:

### 1) add

description: Create a rule in the security group.

parameters:

1. `network`. A cidr string for checking client ip.

2. `protocol`. Enum {TCP, UDP}.

3. `portrange`. A tuple of integer for vproxy port, 0 <= first <= second <= 65535.

4. `dft`. Enum {allow, deny}
if set to allow, then will allow the connection if matches
if set to deny, then will deny the connection if matches.

examples:

```
$ add security-group-rule secgr0 to security-group secg0 network 10.127.0.0/16 protocol TCP port-range 22,22 default allow
"OK"
```

### 2) list

description: Retrieve names of all rules in a security group.

examples:

```
$ list security-group-rule in security-group secg0
1) "secgr0"
```

### 3) listdetail

description: Retrieve detailed info of all rules in a security group.

examples:

```
$ list-detail security-group-rule in security-group secg0
1) "secgr0 -> allow 10.127.0.0/16 protocol TCP port [22,33]"
```

### 4) remove

description: Remove a rule from a security group.

examples:

```
$ remove security-group-rule secgr0 from security-group secg0
"OK"
```

## 11. cert-key

short version: ck

description: Some certificates and one key.

actions:

### 1) add

description: Load certificates and key from file.

parameters:

1. `cert`. The cert file path. Multiple files are separated with `,`.

2. `key`. The key file path.

examples:

```
$ add cert-key vproxy.cassite.net cert ~/cert.pem key ~/key.pem
"OK"
```

### 2) list

description: View loaded cert-key resources.

examples:

```
$ list cert-key
1) "vproxy.cassite.net"
```

### 3) remove

description: Remove a cert-key resource.

examples:

```
$ remove cert-key vproxy.cassite.net
"OK"
```

## 12. dns-cache

description: The dns record cache. It's a host -> ipv4List, ipv6List map. It can only be accessed from the (default) dns resolver.

actions:

### 1) list

description: Count current cache.

examples:

```
$ list dns-cache in resolver (default)
(integer) 1
```

### 2) listdetail

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

### 3) forceremove

description: Specify the host and remove the dns cache.

examples:

```
$ force-remove dns-cache localhost from resolver (default)
"OK"
```

## 13. server-sock

short version: ss

description: Represents a `ServerSocketChannel`, which binds an ip:port.

actions:

### 1) list

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

### 2) listdetail

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

## 14. connection

short version: conn

description: Represents a `SocketChannel`.

actions:

### 1) list

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

### 2) listdetail

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

### 3) forceremove

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

## 15. session

short version: sess

description: Represents a tuple of connections: the connection from client to lb, and the connection from lb to backend server.

actions:

### 1) list

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

### 2) listdetail

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

### 3) forceremove

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

## 16. bytes-in

short version: bin

description: Statistics: bytes flow from remote to local.

actions:

### 1) list

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

## 17. bytes-out

short version: bout

description: Statistics: bytes flow from local to remote.

actions:

### 1) list

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

## 18. accepted-conn-count

description: Statistics: successfully accpeted connections.

actions:

### 1) list

description: Get history total accepted connection count.

examples:

```
$ list accepted-conn-count in server-sock 127.0.0.1:6380 in tl lb0
(integer) 2
```

## 19. switch

short version: sw

description: A switch for vproxy wrapped vxlan packets.

actions:

### 1) add

description: Create a switch.

parameters:

1. `address`. Binding udp address of the switch for wrapped vxlan packets.

2. `mactabletimeout`. [optional] [default value: 300000] Timeout for mac table (ms).

3. `arptabletimeout`. [optional] [default value: 14400000] Timeout for arp table (ms).

4. `eventloopgroup`. [optional] [default value: (worker-elg)] The event loop group used for handling packets.

5. `securitygroup`. [optional] [default value: (allow-all)] The security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected).

6. `mtu`. [optional] [default value: 1500] Default mtu setting for new connected ports.

7. `flood`. [optional] [default value: allow] Default flood setting for new connected ports.

examples:

```
$ add switch sw0 address 0.0.0.0:4789
"OK"
```

### 2) update

description: Update a switch.

parameters:

1. `mactabletimeout`. [optional] [default value: not changed] Timeout for mac table (ms).

2. `arptabletimeout`. [optional] [default value: not changed] Timeout for arp table (ms).

3. `securitygroup`. [optional] [default value: not changed] The security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected).

4. `mtu`. [optional] [default value: not changed] Default mtu setting for new connected ports, updating it will not affect the existing ones.

5. `flood`. [optional] [default value: not changed] Default flood setting for new connected ports, updating it will not affect the existing ones.

examples:

```
$ update switch sw0 mac-table-timeout 60000 arp-table-timeout 120000
"OK"
```

### 3) list

description: Get names of switches.

examples:

```
$ list switch
1) "sw0"
```

### 4) listdetail

description: Get detailed info of switches.

examples:

```
$ list-detail switch
1) "sw0" -> event-loop-group worker bind 0.0.0.0:4789 password p@sSw0rD mac-table-timeout 300000 arp-table-timeout 14400000 bare-vxlan-access (allow-all)
```

### 5) remove

description: Stop and remove a switch.

examples:

```
$ remove switch sw0
"OK"
```

### 6) addto

description: Add a remote switch ref to a local switch. note: use list iface to see these remote switches.

flags:

1. `noswitchflag`. [optional] Do not add switch flag on vxlan packets sent through this iface.

parameters:

1. `address`. The remote switch address.

examples:

```
$ add switch sw1 to switch sw0 address 100.64.0.1:18472
"OK"
```

### 7) removefrom

description: Remove a remote switch ref from a local switch.

examples:

```
$ remove switch sw1 from switch sw0
"OK"
```

## 20. vpc

description: A private network.

actions:

### 1) list

description: List existing vpcs in a switch.

examples:

```
$ list vpc in switch sw0
1) (integer) 1314
```

### 2) listdetail

description: List detailed info about vpcs in a switch.

examples:

```
$ list-detail vpc in switch sw0
1) "1314 -> v4network 172.16.0.0/16"
```

### 3) addto

description: Create a vpc in a switch. the name should be vni of the vpc.

parameters:

1. `v4network`. The ipv4 network allowed in this vpc.

2. `v6network`. [optional] [default value: not allowed] The ipv6 network allowed in this vpc.

examples:

```
$ add vpc 1314 to switch sw0 v4network 172.16.0.0/16
"OK"
```

### 4) removefrom

description: Remove a vpc from a switch.

examples:

```
$ remote vpc 1314 from switch sw0
"OK"
```

## 21. iface

description: Connected interfaces.

actions:

### 1) list

description: Count currently connected interfaces in a switch.

examples:

```
$ list iface in switch sw0
(integer) 2
```

### 2) listdetail

description: List current connected interfaces in a switch.

examples:

```
$ list-detail iface in switch sw0
1) "Iface(192.168.56.2:8472)"
2) "Iface(100.64.0.4:8472)"
```

### 3) update

description: Update interface config.

parameters:

1. `mtu`. [optional] [default value: 1500] Mtu of this interface.

2. `flood`. [optional] [default value: allow] Whether to allow flooding traffic through this interface, allow or deny.

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

## 22. arp

description: Arp and mac table entries.

actions:

### 1) list

description: Count entries in a vpc.

examples:

```
$ list arp in vpc 1314 in switch sw0
(integer) 2
```

### 2) listdetail

description: List arp and mac table entries in a vpc.

examples:

```
$ list-detail arp in vpc 1314 in switch sw0
1) "aa:92:96:2f:3b:7d        10.213.0.1             Iface(127.0.0.1:54042)        ARP-TTL:14390        MAC-TTL:299"
2) "fa:e8:aa:6c:45:f4        10.213.0.2             Iface(127.0.0.1:57374)        ARP-TTL:14390        MAC-TTL:299"
```

## 23. user

description: User in a switch.

actions:

### 1) list

description: List user names in a switch.

examples:

```
$ list user in switch sw0
1) "hello"
```

### 2) listdetail

description: List all user info in a switch.

examples:

```
$ list-detail user in switch sw0
1) "hello" -> vni 1314
```

### 3) add

description: Add a user to a switch.

parameters:

1. `pass`. Password of the user.

2. `vni`. Vni assigned for the user.

3. `mtu`. [optional] [default value: mtu setting of the switch] Mtu for the user interface when the user is connected.

4. `flood`. [optional] [default value: flood setting of the switch] Whether the user interface allows flooding traffic.

examples:

```
$ add user hello to switch sw0 vni 1314 password p@sSw0rD
"OK"
```

### 4) update

description: Update user info in a switch.

parameters:

1. `mtu`. [optional] [default value: not changed] Mtu for the user interface when the user is connected, updating it will not affect connected ones.

2. `flood`. [optional] [default value: not changed] Whether the user interface allows flooding traffic, updating it will not affect connected ones.

examples:

```
$ update user hello in switch sw0 mtu 1500 flood allow
"OK"
```

### 5) remove

description: Remove a user from a switch.

examples:

```
$ remove user hello from switch sw0
"OK"
```

## 24. tap

description: Add/remove a tap device and bind/detach it to/from a switch. The input alias may also be a pattern, see linux tuntap manual. Note: 1) use list iface to see these tap devices, 2) should set -Dvfd=posix or -Dvfd=windows.

actions:

### 1) addto

description: Add a user to a switch. Note: the result string is the name of the tap device because might be generated.

parameters:

1. `vni`. Vni of the vpc which the tap device is attached to.

2. `postscript`. [optional] [default value: (empty)] Post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch).

3. `mtu`. [optional] [default value: mtu setting of the switch] Mtu of this tap device.

4. `flood`. [optional] [default value: flood setting of the switch] Whether the tap device allows flooding traffic.

examples:

```
$ add tap tap%d to switch sw0 vni 1314
"tap0"
```

### 2) removefrom

description: Remove and close a tap from a switch.

examples:

```
$ remove tap tap0 from switch sw0
"OK"
```

## 25. tun

description: Add/remove a tun device and bind/detach it to/from a switch. The input alias may also be a pattern, see linux tuntap manual. Note: 1) use list iface to see these tun devices, 2) should set -Dvfd=posix.

actions:

### 1) addto

description: Add a user to a switch. Note: the result string is the name of the tun device because might be generated.

parameters:

1. `vni`. Vni of the vpc which the tun device is attached to.

2. `mac`. Mac address of this tun device. the switch requires l2 layer frames for handling packets.

3. `postscript`. [optional] [default value: (empty)] Post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch).

4. `mtu`. [optional] [default value: mtu setting of the switch] Mtu of this tun device.

5. `flood`. [optional] [default value: flood setting of the switch] Whether the tun device allows flooding traffic.

examples:

```
$ add tun tun%d to switch sw0 vni 1314
"tun0"
```

```
$ add tun utun9 to switch sw0 vni 1314
"utun9"
```

### 2) removefrom

description: Remove and close a tun from a switch.

examples:

```
$ remove tun tun0 from switch sw0
"OK"
```

## 26. user-client

short version: ucli

description: User client of an encrypted tunnel to remote switch. Note: use list iface to see these clients.

actions:

### 1) add

description: Add a user client to a switch.

parameters:

1. `pass`. Password of the user.

2. `vni`. Vni which the user is assigned to.

3. `address`. Remote switch address to connect to.

examples:

```
$ add user-client hello to switch sw0 password p@sSw0rD vni 1314 address 192.168.77.1:18472
"OK"
```

### 2) remove

description: Remove a user client from a switch.

parameters:

1. `address`. Remote switch address the client connected to.

examples:

```
$ remove user-client hello from switch sw0 address 192.168.77.1:18472
"OK"
```

## 27. ip

description: Synthetic ip in a vpc of a switch.

actions:

### 1) list

description: Show synthetic ips in a vpc of a switch.

examples:

```
$ list ip in vpc 1314 in switch sw0
1) "172.16.0.21"
2) "[2001:0db8:0000:f101:0000:0000:0000:0002]"
```

### 2) listdetail

description: Show detailed info about synthetic ips in a vpc of a switch.

examples:

```
$ list-detail ip in vpc 1314 in switch sw0
1) "172.16.0.21 -> mac e2:8b:11:00:00:22"
2) "[2001:0db8:0000:f101:0000:0000:0000:0002] -> mac e2:8b:11:00:00:33"
```

### 3) addto

description: Add a synthetic ip to a vpc of a switch.

parameters:

1. `mac`. Mac address that the ip assigned on.

examples:

```
$ add ip 172.16.0.21 to vpc 1314 in switch sw0 mac e2:8b:11:00:00:22
"OK"
```

### 4) removefrom

description: Remove a synthetic ip from a vpc of a switch.

examples:

```
$ remove ip 172.16.0.21 from vpc 1314 in switch sw0
"OK"
```

## 28. route

description: Route rules in a vpc of a switch.

actions:

### 1) list

description: Show route rule names in a vpc of a switch.

examples:

```
$ list route in vpc 1314 in switch sw0
1) "to172.17"
2) "to2001:0db8:0000:f102"
```

### 2) listdetail

description: Show detailed info about route rules in a vpc of a switch.

examples:

```
$ list-detail route in vpc 1314 in switch sw0
1) "to172.17 -> network 172.17.0.0/24 vni 1315"
2) "to2001:0db8:0000:f102 -> network [2001:0db8:0000:f102:0000:0000:0000:0000]/64 vni 1315"
```

### 3) addto

description: Add a route to a vpc of a switch.

parameters:

1. `network`. Network to be matched.

2. `vni`. The vni to send packet to. only one of vni|via can be used.

3. `via`. The address to forward the packet to. only one of via|vni can be used.

examples:

```
$ add route to172.17 to vpc 1314 in switch sw0 network 172.17.0.0/24 vni 1315
"OK"
```

```
$ add route to172.17 to vpc 1314 in switch sw0 network 172.17.0.0/24 via 172.16.0.1
"OK"
```

### 4) removefrom

description: Remove a route rule from a vpc of a switch.

examples:

```
$ remove route to172.17 from vpc 1314 in switch sw0
"OK"
```
