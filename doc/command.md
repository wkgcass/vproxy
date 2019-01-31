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
list server-groups                   --- no alias
add server-groups name               --- an alias is required when action is not list nor list-detail
```

where `server-groups` is a `$resource-type`. The command means to list all resources with type `server-groups` on top level.

There are many kinds of `$resource-type`s, as shown in this figure:

```
+---+ tcp-lb (tl)
+---+ socks5-server (socks5)
+---+ event-loop-group (elg)
|        |
|        +---+ event-loop (el)
+---+ server-groups (sgs)
|        |
|        +---+ server-group (sg)
+---+ server-group (sg)
|        |
|        +---+ server (svr)
+---+ security-group (secg)
         |
         +---+ security-group-rule (secgr)

   bind-server (bs) --+
  connection (conn)   +-- /* channel */
     session (sess) --+

            persist --+-- /* state */
          dns-cache --+

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

## Action: force-remote (R)

Remove and destroy/stop a resource, regardless of warnings.

## Action: remove ... from ... (r ... from ...)

Detach a resource from another one.

## Resource: tcp-lb (tl)

TCP load balancer

#### add

Create a loadbalancer.

* acceptor-elg (aelg): choose an event loop group as the acceptor event loop group. can be the same as worker event loop group
* event-loop-group (elg): choose an event loop group as the worker event loop group. can be the same as acceptor event loop group
* address (addr): the bind address of the loadbalancer
* server-groups (sgs): used as the backend servers
* in-buffer-size: input buffer size. *optional*, default 16384 (bytes)
* out-buffer-size: output buffer size. *optional*, default 16384 (bytes)
* persist: an integer representing the timeout (ms) of how long to persist a connector for a client ip. *optional*, default 0, means do not persist
* security-group (secg): specify a security group for the lb. *optional*, default allow any

```
add tcp-lb lb0 acceptor-elg elg0 event-loop-group elg0 address 127.0.0.1:18080 server-groups sgs0 in-buffer-size 16384 out-buffer-size 16384
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
1) "lb0 -> acceptor elg0 worker elg0 bind 127.0.0.1:18080 backends sgs0 in buffer size 16384 out buffer size 16384"
```

#### update

Update persist, in-buffer-size or out-buffer-size of an lb.

```
update tcp-lb lb0 persist 10000 in-buffer-size 32768 out-buffer-size 32768
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

All params are the same as creating `tcp-lb`, but does not support `persist`.  
See `add tcp-lb` for more info.

* acceptor-elg (aelg): the acceptor event loop
* event-loop-group (elg): the worker event loop
* address (addr): the bind address
* server-groups (sgs): used as backends, the socks5 only supports servers added into this group
* in-buffer-size: input buffer size
* out-buffer-size: output buffer size
* security-group (secg): security group

```
add socks5-server s5 acceptor-elg acceptor event-loop-group worker address 127.0.0.1:18081 server-groups backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0
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
1) "s5 -> acceptor acceptor worker worker bind 127.0.0.1:18081 backends backend-groups in buffer size 16384 out buffer size 16384 security-group secg0"
```

#### update

Update in-buffer-size or out-buffer-size of a socks5 server.

```
update socks5-server s5 in-buffer-size 8192 out-buffer-size 8192
"OK"
```

#### remove

Remove a socks5 server.

```
remove socks5-server s5
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

## Resource: server-groups (sgs)

A resource containing multiple `server-group` resources.

#### add

Specify a name and create a `server-groups`.

```
add server-groups sgs0
"OK"
```

#### list/list-detail

Retrieve names of all `server-groups` resources.

```
list server-groups
1) "sgs0"
list-detail server-groups
1) "sgs0"
```

#### remove

Remove a `server-groups` resource.

```
remove server-groups sgs0
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
* method: loadbalancing algorithm, `wrr` or `wlc`. *optional*, default `wrr`
* event-loop-group (elg): choose a event-loop-group for the server group. health check operations will be performed on the event loop group

```
add server-group sg0 timeout 500 period 800 up 4 down 5 method wrr elg elg0
"OK"
```

#### add to

Attach an existing server group into `server-groups`.

* weight (w): the weight of group in this server-groups resource

```
add server-group sg0 to server-groups sgs0 weight 10
"OK"
```

#### list

Retrieve names of all server group (s) on top level or in a `server-groups`.

```
list server-group
1) "sg0"

list server-group in server-groups sgs0
1) "sg0"
```

#### list-detail

Retrieve detailed info of all server group (s).

```
list-detail server-group
1) "sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0"

list-detail server-group in server-groups sgs0
1) "sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 weight 10"
```

#### update

Change health check config or load balancing algorithm.

Param list is the same as add, but not all required.

Also you can change the weight of a group in a `server-groups` resource.

```
update server-group sg0 timeout 500 period 600 up 3 down 2
"OK"

update server-group sg0 method wlc
"OK"

update server-group sg0 in server-groups sgs0 weight 5
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

Detach the group grom a `server-groups` resource.

```
remove server-group sg0 from server-groups sgs0
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

Specify name, remote ip:port, local request ip, weight, and attach the server into the server group

* address (addr): remote address, ip:port
* ip (via): local request ip address
* weight: weight of the server, which will be used by wrr and wlc algorithm

```
add server svr0 to server-group sg0 address 127.0.0.1:6379 via 127.0.0.1 weight 10
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
1) "svr0 -> connect to 127.0.0.1:6379 via 127.0.0.1 weight 10 currently DOWN"
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

## Resource: persist

Represents a persisted connector, which contains client source ip, which server to use and request server with which local ip.  
The resource only exist in `tcp-lb`.

#### list

Count persisted connectors.

```
list persist in tl lb0
(integer) 1
```

#### list-detail

Retrieve detailed info of all persisted connectors.

```
list-detail persist in tl lb0
1) 1) "client: 127.0.0.1"
   2) "server: 127.0.0.1:16666"
   3) "   via: 127.0.0.1:0"
```

#### force-remove

Specify the source (client) address string and remove the persist record.

```
force-remove persist 127.0.0.1 from tcp-lb lb0
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

## Resource: bind-server (bs)

Represents a `ServerSocketChannel`, which binds an ip:port.

#### list

Count bind servers. Can be retrieved from `event-loop`, `tcp-lb`, `socks5-server`.

```
list bind-server in el el0 in elg elg0
(integer) 1

list bind-server in tcp-lb lb0
(integer) 1

list bind-server in socks5-server s5
(integer) 1
```

#### list-detail

Get info about bind servers. Can be retrieved from `event-loop`, `tcp-lb`, `socks5-server`.

```
list-detail bind-server in el el0 in elg elg0
1) "127.0.0.1:6380"

list-detail bind-server in tcp-lb lb0
1) "127.0.0.1:6380"

list-detail bind-server in socks5-server s5
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

Get history total input bytes from a resource. Can be retrieved from `bind-server`, `connection`, `server`.

```
list bytes-in in bind-server 127.0.0.1:6380 in tl lb0
(integer) 45

list bytes-in in connection 127.0.0.1:63536/127.0.0.1:6380 in el el0 in elg elg0
(integer) 45

list bytes-in in server svr0 in sg sg0
(integer) 9767
```

## Resource: bytes-out (bout)

Statistics: bytes flow from local to remote.

#### list/list-detail

Get history total output bytes from a resource. Can be retrieved from `bind-server`, `connection`, `server`.

```
list bytes-out in bind-server 127.0.0.1:6380 in tl lb0
(integer) 9767

list bytes-out in connection 127.0.0.1:63536/127.0.0.1:6380 in el el0 in elg elg0
(integer) 9767

list bytes-out in server svr0 in sg sg0
(integer) 45
```

## Resource: accepted-conn-count

Statistics: successfully accpeted connections. Connections accepted by os but directly terminated by the Proxy are not calculated.

#### list/list-detail

Get history total accepted connection count. Can be retrieved from `bind-server`.

```
list accepted-conn-count in bind-server 127.0.0.1:6380 in tl lb0
(integer) 2
```
