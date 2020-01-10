# Command

## General

VProxy provides you with full control of inside components.
As a result, the configuration is a little different from what you might have thought.

VProxy has a very simple configuration syntax.

```
$action $resource-type [$resource-alias] [in $resource-type $resource-alias [in ...]] [to/from $resource-$type $resource-alias] $param-key $param-value $flag
```

There are eight `$action`s in vproxy command:

* `add (a)`: create a resource
* `add (a) ... to ...`: attach a resource to another one
* `list (l)`: show brief info about some resources
* `list-detail (L)`: show detailed info about some resources
* `update (u)`: modify a resource
* `remove (r)`: remove and destroy a resource
* `force-remove (R)`: ignore warnings and remove a resource
* `remove (r) ... from ...`: detach a resource from another one

If the `$action` is `list` or `list-detail`, the first `$resource-alias` should not be specified, otherwise, it's required:

```
list upstream                   --- no alias
add upstream name               --- an alias is required when action is not list nor list-detail
```

where `upstream` is a `$resource-type`. The command means to list all resources with type `upstream` on top level.

There are many kinds of `$resource-type`s, as shown in this figure:

```
+---+ tcp-lb (tl)
+---+ socks5-server (socks5)
+---+ event-loop-group (elg)
|        |
|        +---+ event-loop (el)
+---+ upstream (ups)
|        |
|        +---+ server-group (sg)
+---+ server-group (sg)
|        |
|        +---+ server (svr)
+---+ security-group (secg)
|        |
|        +---+ security-group-rule (secgr)
|
+---+ cert-key (ck)
|
+---+ smart-group-delegate
+---+ smart-node-delegate

   server-sock (ss) --+
  connection (conn)   +-- /* channel */
     session (sess) --+

          dns-cache ----- /* state */

     bytes-in (bin) --+
   bytes-out (bout)   +-- /* statistics */
accepted-conn-count --+

short version keywords are between `()`
```

The resource types form a tree structure. This corresponds to the vproxy architecture figure in README.md.

For some resources which can be directly accessed from tree root, we call them `on top level`.  
For some other resources that are not on top level, we use a keyword `in` to access them, e.g.:

```
event-loop [$alias] in event-loop-group $alias
```

which tries to access some `event-loop` in an event-loop-group.

There can be multiple `in` to represent a single resource, e.g.:

```
l bytes-in in conn 127.0.0.1:56727/127.0.0.1:6379 in svr svr0 in sg sg0
```

which tries to get `input bytes` of a connection `127.0.0.1:56727/127.0.0.1:6379`. To retrieve the connection, we first get a `server-group` named `sg0`, and get `server` named `svr0` from `sg0`, then we can retrieve the connection from the `svr0`.

The params and flags are simple. Params are pairs of "key"s and "value"s. Flags represent booleans.

## Action: add (a)

Create a resource.

## Action: add ... to ... (a ... to ...)

Attach a resource to another one.

## Action: list (l)

List names, or retrieve count of some resources.

## Action: list-detail (L)

List detailed info of some resources.

## Action: update (u)

Modify a resource.

## Action: remove (r)

Remove and destroy/stop a resource. If the resource is being used by another one, a warning will be returned and operation will be aborted.

## Action: force-remove (R)

Remove and destroy/stop a resource, regardless of warnings.

## Action: remove ... from ... (r ... from ...)

Detach a resource from another one.

## Resource: tcp-lb (tl)

TCP load balancer

#### add

Create a loadbalancer.

* acceptor-elg (aelg): *optional*. choose an event loop group as the acceptor event loop group. can be the same as worker event loop group.
* event-loop-group (elg): *optional*. choose an event loop group as the worker event loop group. can be the same as acceptor event loop group.
* address (addr): the bind address of the loadbalancer
* upstream (ups): used as the backend servers
* in-buffer-size: *optional*. input buffer size. default 16384 (bytes)
* out-buffer-size: *optional*. output buffer size. default 16384 (bytes)
* protocol: *optional*. the protocol used by tcp-lb. available options: tcp, http, h2, http/1.x, dubbo, framed-int32, or your customized protocol. See [doc](https://github.com/wkgcass/vproxy/blob/master/doc/using-application-layer-protocols.md) or [doc_zh](https://github.com/wkgcass/vproxy/blob/master/doc_zh/using-application-layer-protocols.md) for more info. default tcp
* security-group (secg): *optional*. specify a security group for the lb. default allow any

```
add tcp-lb lb0 address 127.0.0.1:18080 upstream ups0
"OK"
```

#### list

Retrieve names of all tcp-loadbalancers.

```
list tcp-lb
1) "lb0"
```

#### list-detail

Retrieve detailed info of all tcp-loadbalancers.

```
list-detail tcp-lb
1) "lb0 -> acceptor elg0 worker elg0 bind 127.0.0.1:18080 backend ups0 in-buffer-size 16384 out-buffer-size 16384 protocol tcp security-group secrg0"
```

#### update

Update in-buffer-size or out-buffer-size or security-group of an lb.

```
update tcp-lb lb0 in-buffer-size 32768 out-buffer-size 32768 security-group secg0
"OK"
```

> You can miss some of the params, and only specified params will be updated.

#### remove

Remove and stop a tcp-loadbalancer. The already established connections won't be affected.

```
remove tcp-lb lb0
"OK"
```

## Resource: socks5-server (socks5)

Socks5 proxy server.

#### add

Create a socks5 server.

All params are the same as creating `tcp-lb`.  
See `add tcp-lb` for more info.

* acceptor-elg (aelg): *optional*, the acceptor event loop.
* event-loop-group (elg): *optional*. the worker event loop.
* address (addr): the bind address
* upstream (ups): used as backend, the socks5 only supports servers added into this group
* in-buffer-size: *optional*. input buffer size.
* out-buffer-size: *optional*. output buffer size.
* security-group (secg): security group

Flags:

* allow-non-backend: *optional*. allow to access non backend endpoints.
* deny-non-backend: *optional*. only able to access backend endpoints. the default flag.

```
add socks5-server s5 address 127.0.0.1:18081 upstream backend-groups security-group secg0
"OK"
```

#### list

Retrieve names of socks5 servers.

```
list socks5-server
1) "s5"
```

#### list-detail

Retrieve detailed info of socks5 servers.

```
list-detail socks5-server
1) "s5 -> acceptor acceptor worker worker bind 127.0.0.1:18081 backend backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0"
```

#### update

Update in-buffer-size or out-buffer-size or security-group of a socks5 server. Also, whether to allow non backend endpoints can be updated.

```
update socks5-server s5 in-buffer-size 8192 out-buffer-size 8192 security-group secg0 allow-non-backend
"OK"
```

#### remove

Remove a socks5 server.

```
remove socks5-server s5
"OK"
```

## Resource: dns-server (dns)

DNS Server

#### add

Create a dns server.

* address (addr): The bind address of the socks5 server.
* upstream (ups): The domains to be resolved.
* ttl: *optional* The ttl of responded records. Default: 0
* event-loop-group: *optional* Choose an event loop group to run the dns server. Default: (worker-elg)

```
add dns-server dns0 address 127.0.0.1:53 upstream backend-groups ttl 0
"OK"
```

#### update

Update config of a dns server.

* ttl: *optional* The ttl of responded records. Default: not changed

```
update dns-server dns0 ttl 60
"OK"
```

#### list

Retrieve names of dns servers.

```
list dns-server
1) "dns0"
```

#### list-detail

Retrieve detailed info of dns servers.

```
list-detail dns-server
1) "dns0 -> event-loop-group worker bind 127.0.0.1:53 backend backend-groups"
```

#### remove

Remove a dns server.

```
remove dns-server dns0
"OK"
```

## Resource: event-loop-group (elg)

A group of event loops

#### add

Specify a name and create a event loop group

```
add event-loop-group elg0
"OK"
```

#### list/list-detail

Retrieve names of all event loop groups

```
list event-loop-group
1) "elg0"
list-detail event-loop-group
1) "elg0"
```

#### remove

Remove a event loop group

```
remove event-loop-group elg0
"OK"
```

## Resource: upstream (ups)

A resource containing multiple `server-group` resources.

#### add

Specify a name and create a `upstream`.

```
add upstream ups0
"OK"
```

#### list/list-detail

Retrieve names of all `upstream` resources.

```
list upstream
1) "ups0"
list-detail upstream
1) "ups0"
```

#### remove

Remove a `upstream` resource.

```
remove upstream ups0
"OK"
```

## Resource: server-group

A group of remote servers, which will run health check for all contained servers.

#### add

Specify name, event loop, load balancing method, health check config and create a server group.

* timeout: health check connect timeout (ms)
* period: do check every `${period}` milliseconds
* up: set server status to UP after succeeded for `${up}` times
* down: set server status to DOWN after failed for `${down}` times
* protocol: *optional*. the protocol used for checking the servers, you may choose `tcp`, `none`. default `tcp`
* method: *optional*. loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`. default `wrr`
* annotations: *optional*. extra info for the server-group, such as host info, health check url. Must be a json and values must be strings. default `{}`
* event-loop-group (elg): *optional*. choose a event-loop-group for the server group. health check operations will be performed on the event loop group.

```
add server-group sg0 timeout 500 period 800 up 4 down 5 method wrr
"OK"
```

#### add to

Attach an existing server group into `upstream`.

* weight (w): the weight of group in this upstream resource

```
add server-group sg0 to upstream ups0 weight 10
"OK"
```

#### list

Retrieve names of all server group (s) on top level or in a `upstream`.

```
list server-group
1) "sg0"

list server-group in upstream ups0
1) "sg0"
```

#### list-detail

Retrieve detailed info of all server group (s).

```
list-detail server-group
1) "sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 annotations {}"

list-detail server-group in upstream ups0
1) "sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 annotations {} weight 10"
```

#### update

Change health check config or load balancing algorithm.

Param list is the same as add, but not all required. Note that if you change the health check related params and not specifying `procotol`, it will be set to `tcp` as default.

Also you can change the weight/annotations of a group in a `upstream` resource.

```
update server-group sg0 timeout 500 period 600 up 3 down 2 protocol tcp
"OK"

update server-group sg0 method wlc
"OK"

update server-group sg0 in upstream ups0 weight 5
"OK"
```

> NOTE: all fields in health check config should be all specified if any one of them exists.

#### remove

Remove a server group.

```
remove server-group sg0
"OK"
```

#### remove from

Detach the group grom a `upstream` resource.

```
remove server-group sg0 from upstream ups0
"OK"
```

## Resource: event-loop (el)

#### add to

Specify a name, a event loop group, and create a new event loop in the specified group.

```
add event-loop el0 to elg elg0
"OK"
```

#### list/list-detail

Retrieve names of all event loops in a event loop group.

```
list event-loop in event-loop-group elg0
1) "el0"
list-detail event-loop in event-loop-group elg0
1) "el0"
```

#### remove from

Remove a event loop from event loop group.

```
remove event-loop el0 from event-loop-group elg0
"OK"
```

## Resource: server (svr)

#### add to

Specify name, remote ip:port, weight, and attach the server into the server group

* address (addr): remote address, ip:port
* weight: weight of the server, which will be used by wrr, wlc and source algorithm

```
add server svr0 to server-group sg0 address 127.0.0.1:6379 weight 10
"OK"
```

#### list

Retrieve names of all servers in a server group.

```
list server in server-group sg0
1) "svr0"
```

#### list-detail

Retrieve detailed info of all servers in a server group.

```
list-detail server in server-group sg0
1) "svr0 -> connect-to 127.0.0.1:6379 weight 10 currently DOWN"
```

#### update

Change weight of the server.

```
update server svr0 in server-group sg0 weight 11
"OK"
```

#### remove from

Remove a server from a server group.

```
remove server svr0 from server-group sg0
"OK"
```

## Resource: security-group (secg)

A white/black list, see `security-group-rule` for more info.

#### add

Create a security group.

* default: enum {allow, deny}  
    if set to allow, then will allow connection if all rules not match  
    if set to deny, then will deny connection if all rules not match

```
add security-group secg0 default allow
"OK"
```

#### list

Retrieve names of all security groups.

```
list security-group
1) "secg0"
```

#### list-detail

Retrieve detailed info of all security groups.

```
list-detail security-group
1) "secg0 -> default allow"
```

#### update

Update properties of a security group.

```
update security-group secg0 default deny
"OK"
```

#### remove

Remove a security group.

```
remove security-group secg0
"OK"
```

## Resource: security-group-rule

A rule containing protocol, source network, dest port range and whether to deny.

#### add

Create a rule in the security group.

* network (net): a cidr string for checking client ip
* protocol: enum {TCP, UDP}
* port-range: a tuple of integer for vproxy port, 0 <= first <= second <= 65535
* default: enum {allow, deny}  
    if set to allow, then will allow the connection if matches  
    if set to deny, then will deny the connection if matches

> NOTE: network is for client (source ip), and port-range is for vproxy (destination port).

```
add security-group-rule secgr0 to security-group secg0 network 10.127.0.0/16 protocol TCP port-range 22,22 default allow
"OK"
```

#### list

Retrieve names of all rules in a security group.

```
list security-group-rule in security-group secg0
1) "secgr0"
```

#### list-detail

Retrieve detailed info of all rules in a security group.

```
list-detail security-group-rule in security-group secg0
1) "secgr0 -> allow 10.127.0.0/16 protocol TCP port [22,33]"
```

#### remove

Remove a rule from a security group.

```
remove security-group-rule secgr0 from security-group secg0
"OK"
```

## Resource: cert-key

A resource corresponds to certificates and a key.

#### add

Load certificates and a key from file.

* cert: the certificate files, separated with `,`
* key: the key file. only `-----BEGINE PRIVATE KEY-----` format is supported for now

```
add cert-key vproxy.cassite.net cert ~/cert.pem key ~/key.pem
"OK"
```

#### list

Get names of cert-key info.

```
list cert-key
1) vproxy.cassite.net
```

#### remove

Remove a cert-key.

```
remove cert-key vproxy.cassite.net
"OK"
```

## Resource: dns-cache

The dns record cache. It's a `host -> ipv4List, ipv6List` map.  
It can only be accessed from the `(default)` dns resolver.

#### list

Count current cache

```
list dns-cache in resolver (default)
(integer) 1
```

#### list-detail

List detailed info of dns cache.

The return values are:

* host
* ipv4 ip list
* ipv6 ip list

```
list-detail dns-cache in resolver (default)
1) 1) "localhost"
   2) 1) "127.0.0.1"
   3) 1) "[0000:0000:0000:0000:0000:0000:0000:0001]"
```

#### force-remove

Specify the host and remove the dns cache.

```
force-remove dns-cache localhost from resolver (default)
"OK"
```

## Resource: server-sock (ss)

Represents a `ServerSocketChannel`, which binds an ip:port.

#### list

Count server-socks. Can be retrieved from `event-loop`, `tcp-lb`, `socks5-server`.

```
list server-sock in el el0 in elg elg0
(integer) 1

list server-sock in tcp-lb lb0
(integer) 1

list server-sock in socks5-server s5
(integer) 1
```

#### list-detail

Get info about server-socks. Can be retrieved from `event-loop`, `tcp-lb`, `socks5-server`.

```
list-detail server-sock in el el0 in elg elg0
1) "127.0.0.1:6380"

list-detail server-sock in tcp-lb lb0
1) "127.0.0.1:6380"

list-detail server-sock in socks5-server s5
1) "127.0.0.1:18081"
```

## Resource: connection (conn)

Represents a `SocketChannel`.

#### list

Count connections. Can be retrieved from `event-loop`, `tcp-lb`, `socks5-server`, `server`.

```
list connection in el el0 in elg elg0
(integer) 2

list connection in tcp-lb lb0
(integer) 2

list connection in socks5-server s5
(integer) 2

list connection in server svr0 in sg sg0
(integer) 1
```

#### list-detail

Get info about connections. Can be retrieved from `event-loop`, `tcp-lb`, `socks5-server`, `server`.

```
list-detail connection in el el0 in elg elg0
1) "127.0.0.1:63537/127.0.0.1:6379"
2) "127.0.0.1:63536/127.0.0.1:6380"

list-detail connection in tcp-lb lb0
1) "127.0.0.1:63536/127.0.0.1:6380"
2) "127.0.0.1:63537/127.0.0.1:6379"

list-detail connection in socks5-server s5
1) "127.0.0.1:55981/127.0.0.1:18081"
2) "127.0.0.1:55982/127.0.0.1:16666"

list-detail connection in server svr0 in sg sg0
1) "127.0.0.1:63537/127.0.0.1:6379"
```

#### force-remove

Close the connection, and if the connection is bond to a session, the session will be closed as well.

Supports regexp pattern or plain string:

* if the input starts with `/` and ends with `/`, then it's considered as a regexp
* otherwise it matches the full string

```
force-remove conn 127.0.0.1:57629/127.0.0.1:16666 from el worker2 in elg worker
"OK"

force-remove conn /.*/ from el worker2 in elg worker
"OK"
```

## Resource: session (sess)

Represents a tuple of connections: the connection from client to lb, and the connection from lb to backend server.

#### list

Count loadbalancer sessions. Can be retrieved from `tcp-lb` or `socks5-server`.

```
list session in tcp-lb lb0
(integer) 1

list session in socks5-server s5
(integer) 2
```

#### list-detail

Get info about loadbalancer sessions. Can be retrieved from `tcp-lb` or `socks5-server`.

```
list-detail session in tcp-lb lb0
1) 1) "127.0.0.1:63536/127.0.0.1:6380"
   2) "127.0.0.1:63537/127.0.0.1:6379"

list-detail session in socks5-server s5
1) 1) "127.0.0.1:53589/127.0.0.1:18081"
   2) "127.0.0.1:53591/127.0.0.1:16666"
2) 1) "127.0.0.1:53590/127.0.0.1:18081"
   2) "127.0.0.1:53592/127.0.0.1:16666"
```

#### force-remove

Close a session from lb. The related two connections will be closed as well.

```
force-remove sess 127.0.0.1:58713/127.0.0.1:18080->127.0.0.1:58714/127.0.0.1:16666 from tl lb0
"OK"

force-remove sess /127.0.0.1:58713.*/ from tl lb0
"OK"
```

## Resource: bytes-in (bin)

Statistics: bytes flow from remote to local.

#### list/list-detail

Get history total input bytes from a resource. Can be retrieved from `server-sock`, `connection`, `server`.

```
list bytes-in in server-sock 127.0.0.1:6380 in tl lb0
(integer) 45

list bytes-in in connection 127.0.0.1:63536/127.0.0.1:6380 in el el0 in elg elg0
(integer) 45

list bytes-in in server svr0 in sg sg0
(integer) 9767
```

## Resource: bytes-out (bout)

Statistics: bytes flow from local to remote.

#### list/list-detail

Get history total output bytes from a resource. Can be retrieved from `server-sock`, `connection`, `server`.

```
list bytes-out in server-sock 127.0.0.1:6380 in tl lb0
(integer) 9767

list bytes-out in connection 127.0.0.1:63536/127.0.0.1:6380 in el el0 in elg elg0
(integer) 9767

list bytes-out in server svr0 in sg sg0
(integer) 45
```

## Resource: accepted-conn-count

Statistics: successfully accpeted connections. Connections accepted by os but directly terminated by the Proxy are not calculated.

#### list/list-detail

Get history total accepted connection count. Can be retrieved from `server-sock`.

```
list accepted-conn-count in server-sock 127.0.0.1:6380 in tl lb0
(integer) 2
```

## Resource: smart-group-delegate

A binding for a server-group with info from vproxy discovery network.

#### add

Create a new smart-group-delegate binding.

* service: the service watched by the delegate
* zone: the zone watched by the delegate
* server-group: the server group to bind

```
add smart-group-delegate sgd0 service myservice zone z0 server-group sg0
"OK"
```

#### list

Get names of smart-group-delegate bindings.

```
list smart-group-delegate
1) "sgd0"
```

#### list-detail

Get detailed info about smart-group-delegate bindings.

```
list-detail smart-group-delegate
1) "sgd0 -> service myservice zone z0 server-group sg0"
```

#### remove

Remove the smart-group-delegate binding.

```
remove smart-group-delegate sgd0
"OK"
```

## Resource: smart-node-delegate

A delegate for a node registered to the vproxy discovery network.

#### add

Create a new smart-node-delegate.

* service: the service handled by the delegate
* zone: the zone handled by the delegate
* nic: which nic the node listens on
* port: which port the node listens on
* ip-type: *optional* which ip type the node listens on, enum: v4 or v6, default v4

```
add smart-node-delegate snd0 service myservice zone z0 nic eth0 port 8080
"OK"
```

#### list

Get names of smart-node-delegate.

```
list smart-node-delegate
1) "snd0"
```

#### list-detail

Get detailed info about smart-node-delegate bindings.

```
list-detail smart-node-delegate
1) "snd0 -> service myservice zone z0 nic eth0 ip-type v4 port 8080"
```

#### remove

Remove the smart-node-delegate binding.

```
remove smart-node-delegate snd0
"OK"
```

