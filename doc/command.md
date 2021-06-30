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
        |
        +---+ event-loop (el)
+---+ upstream (ups)
        |
        +---+ server-group (sg)
+---+ server-group (sg)
        |
        +---+ server (svr)
+---+ security-group (secg)
        |
        +---+ security-group-rule (secgr)

+---+ cert-key (ck)

+---+ switch (sw)

   server-sock (ss) --+
  connection (conn)   +-- /* channel */
     session (sess) --+

          dns-cache --+-- /* state */
                vni --+
                arp --+

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


## Actions

### add

There are eight `$action`s in vproxy command:

* `add (a)`: create a resource
* `add (a) ... to ...`: attach a resource to another one
* `list (l)`: show brief info about some resources
* `list-detail (L)`: show detailed info about some resources
* `update (u)`: modify a resource
* `remove (r)`: remove and destroy a resource
* `remove (r) ... from ...`: detach a resource from another one

### add-to

Attach a resource to another one.

### list

List names, or retrieve count of some resources.

### list-detail

List detailed info of some resources.

### update

Modify a resource.

### remove

Remove and destroy/stop a resource. If the resource is being used by another one, a warning will be returned and operation will be aborted.

### remove-from

Detach a resource from another one.

## Resources

### tcp-lb

short version: `tl`

description: TCP load balancer.

#### actions

##### add

<details><summary>Create a loadbalancer.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|address|The bind address of the loadbalancer.|||
|upstream|Used as the backend servers.|||
|acceptor-elg|Choose an event loop group as the acceptor event loop group. can be the same as worker event loop group.|Y|(acceptor-elg)|
|event-loop-group|Choose an event loop group as the worker event loop group. can be the same as acceptor event loop group.|Y|(worker-elg)|
|in-buffer-size|Input buffer size.|Y|16384 (bytes)|
|out-buffer-size|Output buffer size.|Y|16384 (bytes)|
|timeout|Idle timeout of connections in this lb instance.|Y|900000 (ms)|
|protocol|The protocol used by tcp-lb. available options: tcp, http, h2, http/1.x, dubbo, framed-int32, or your customized protocol. See doc for more info.|Y|tcp|
|cert-key|The certificates and keys used by tcp-lb. Multiple cert-key(s) are separated with `,`.|||
|security-group|Specify a security group for the lb.|Y|allow any|

examples:

```
$ add tcp-lb lb0 acceptor-elg elg0 event-loop-group elg0 address 127.0.0.1:18080 upstream ups0 in-buffer-size 16384 out-buffer-size 16384
"OK"
```

</details>

##### list

## Action: remove ... from ... (r ... from ...)

```
$ list tcp-lb
1) "lb0"
```

</details>

##### list-detail

<details><summary>Retrieve detailed info of all tcp-loadbalancers.</summary>

<br>

examples:

```
$ list-detail tcp-lb
1) "lb0 -> acceptor elg0 worker elg0 bind 127.0.0.1:18080 backend ups0 in-buffer-size 16384 out-buffer-size 16384 protocol tcp security-group secg0"
```

</details>

##### update

<details><summary>Update in-buffer-size or out-buffer-size of an lb.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|in-buffer-size|Input buffer size.|Y|not changed|
|out-buffer-size|Output buffer size.|Y|not changed|
|timeout|Idle timeout of connections in this lb instance.|Y|not changed|
|cert-key|The certificates and keys used by tcp-lb. Multiple cert-key(s) are separated with `,`.|Y|not changed|
|security-group|The security group.|Y|not changed|

examples:

```
$ update tcp-lb lb0 in-buffer-size 32768 out-buffer-size 32768
"OK"
```

</details>

##### remove

<details><summary>Remove and stop a tcp-loadbalancer. The already established connections won't be affected.</summary>

<br>

examples:

```
$ remove tcp-lb lb0
"OK"
```

</details>

### socks5-server

short version: `socks5`

description: Socks5 proxy server.

#### actions

##### add

<details><summary>Create a socks5 server.</summary>

<br>

flags:

|name|description|opt|default|
|---|---|:---:|:---:|
|allow-non-backend|Allow to access non backend endpoints.|Y||
|deny-non-backend|Only enable backend endpoints.|Y|Y|

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|address|The bind address of the loadbalancer.|||
|upstream|Used as the backend servers.|||
|acceptor-elg|Choose an event loop group as the acceptor event loop group. can be the same as worker event loop group.|Y|(acceptor-elg)|
|event-loop-group|Choose an event loop group as the worker event loop group. can be the same as acceptor event loop group.|Y|(worker-elg)|
|in-buffer-size|Input buffer size.|Y|16384 (bytes)|
|out-buffer-size|Output buffer size.|Y|16384 (bytes)|
|timeout|Idle timeout of connections in this socks5 server instance.|Y|900000 (ms)|
|security-group|Specify a security group for the socks5 server.|Y|allow any|

examples:

```
$ add socks5-server s5 acceptor-elg acceptor event-loop-group worker address 127.0.0.1:18081 upstream backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0
"OK"
```

</details>

##### list

<details><summary>Retrieve names of socks5 servers.</summary>

<br>

examples:

```
$ list socks5-server
1) "s5"
```

</details>

##### list-detail

<details><summary>Retrieve detailed info of socks5 servers.</summary>

<br>

examples:

```
$ list-detail socks5-server
1) "s5 -> acceptor acceptor worker worker bind 127.0.0.1:18081 backend backend-groups in-buffer-size 16384 out-buffer-size 16384 security-group secg0"
```

</details>

##### update

<details><summary>Update in-buffer-size or out-buffer-size of a socks5 server.</summary>

<br>

flags:

|name|description|opt|default|
|---|---|:---:|:---:|
|allow-non-backend|Allow to access non backend endpoints.|Y||
|deny-non-backend|Only enable backend endpoints.|Y|Y|

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|in-buffer-size|Input buffer size.|Y|not changed|
|out-buffer-size|Output buffer size.|Y|not changed|
|timeout|Idle timeout of connections in this socks5 server instance.|Y|not changed|
|security-group|The security group.|Y|not changed|

examples:

```
$ update socks5-server s5 in-buffer-size 8192 out-buffer-size 8192
"OK"
```

</details>

##### remove

<details><summary>Remove a socks5 server.</summary>

<br>

examples:

```
$ remove socks5-server s5
"OK"
```

</details>

### dns-server

short version: `dns`

description: Dns server.

#### actions

##### add

<details><summary>Create a dns server.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|address|The bind address of the socks5 server.|||
|upstream|The domains to be resolved.|||
|event-loop-group|Choose an event loop group to run the dns server.|Y|(worker-elg)|
|ttl|The ttl of responded records.|Y|0|
|security-group|Specify a security group for the dns server.|Y|allow any|

examples:

```
$ add dns-server dns0 address 127.0.0.1:53 upstream backend-groups ttl 0
"OK"
```

</details>

##### list

<details><summary>Retrieve names of dns servers.</summary>

<br>

examples:

```
$ list dns-server
1) "dns0"
```

</details>

##### list-detail

<details><summary>Retrieve detailed info of dns servers.</summary>

<br>

examples:

```
$ list-detail dns-server
1) "dns0 -> event-loop-group worker bind 127.0.0.1:53 backend backend-groups security-group (allow-all)"
```

</details>

##### update

<details><summary>Update config of a dns server.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|ttl|The ttl of responded records.|Y|not changed|
|security-group|The security group.|Y|not changed|

examples:

```
$ update dns-server dns0 ttl 60
"OK"
```

</details>

##### remove

<details><summary>Remove a dns server.</summary>

<br>

examples:

```
$ remove dns-server dns0
"OK"
```

</details>

### event-loop-group

short version: `elg`

description: A group of event loops.

#### actions

##### add

<details><summary>Specify a name and create a event loop group.</summary>

<br>

examples:

```
$ add event-loop-group elg0
"OK"
```

</details>

##### list

<details><summary>Retrieve names of all event loop groups.</summary>

<br>

examples:

```
$ list event-loop-group
1) "elg0"
```

```
$ list-detail event-loop-group
1) "elg0"
```

</details>

##### remove

<details><summary>Remove a event loop group.</summary>

<br>

examples:

```
$ remove event-loop-group elg0
"OK"
```

</details>

### upstream

short version: `ups`

description: A resource containing multiple `server-group` resources.

#### actions

##### add

<details><summary>Specify a name and create an upstream resource.</summary>

<br>

examples:

```
$ add upstream ups0
"OK"
```

</details>

##### list

<details><summary>Retrieve names of all upstream resources.</summary>

<br>

examples:

```
$ list upstream
1) "ups0"
```

```
$ list-detail upstream
1) "ups0"
```

</details>

##### remove

<details><summary>Remove an upstream resource.</summary>

<br>

examples:

```
$ remove upstream ups0
"OK"
```

</details>

### server-group

short version: `sg`

description: A group of remote servers, which will run health check for all contained servers.

#### actions

##### add

<details><summary>Specify name, event loop, load balancing method, health check config and create a server group.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|timeout|Health check connect timeout (ms).|||
|period|Do check every `${period}` milliseconds.|||
|up|Set server status to UP after succeeded for `${up}` times.|||
|down|Set server status to DOWN after failed for `${down}` times.|||
|protocol|The protocol used for checking the servers, you may choose `tcp`, `none`.|Y|tcp|
|method|Loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`.|Y|wrr|
|annotations|Extra info for the server-group, such as host info, health check url. Must be a json and values must be strings.|Y|{}|
|event-loop-group|Choose a event-loop-group for the server group. health check operations will be performed on the event loop group.|Y|(control-elg)|

examples:

```
$ add server-group sg0 timeout 500 period 800 up 4 down 5 method wrr elg elg0
"OK"
```

</details>

##### add-to

<details><summary>Attach an existing server group into an `upstream` resource.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|weight|The weight of group in this upstream resource.|Y|10|
|annotations|Extra info for the server-group inside upstream, such as host info. Must be a json and values must be strings.|Y|{}|

examples:

```
$ add server-group sg0 to upstream ups0 weight 10
"OK"
```

</details>

##### list

<details><summary>Retrieve names of all server group (s) on top level or in an upstream.</summary>

<br>

examples:

```
$ list server-group
1) "sg0"
```

```
$ list server-group in upstream ups0
1) "sg0"
```

</details>

##### list-detail

<details><summary>Retrieve detailed info of all server group(s).</summary>

<br>

examples:

```
$ list-detail server-group
1) "sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 annotations {}"
```

```
$ list-detail server-group in upstream ups0
1) "sg0 -> timeout 500 period 800 up 4 down 5 method wrr event-loop-group elg0 annotations {} weight 10"
```

</details>

##### update

<details><summary>Change health check config or load balancing algorithm.Param list is the same as add, but not all required.Also you can change the weight of a group in an upstream resource.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|timeout|Health check connect timeout (ms).|Y|not changed|
|period|Do check every `${period}` milliseconds.|Y|not changed|
|up|Set server status to UP after succeeded for `${up}` times.|Y|not changed|
|down|Set server status to DOWN after failed for `${down}` times.|Y|not changed|
|protocol|The protocol used for checking the servers, you may choose `tcp`, `none`. Note: this field will be set to `tcp` as default when updating other hc options.|Y|not changed|
|method|Loadbalancing algorithm, you can choose `wrr`, `wlc`, `source`.|Y|not changed|
|weight|The weight of group in the upstream resource (only available for server-group in upstream).|Y|not changed|
|annotations|Annotation of the group itself, or the group in the upstream.|Y|not changed|

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

</details>

##### remove

<details><summary>Remove a server group.</summary>

<br>

examples:

```
$ remove server-group sg0
"OK"
```

</details>

##### remove-from

<details><summary>Detach the group from an `upstream` resource.</summary>

<br>

examples:

```
$ remove server-group sg0 from upstream ups0
"OK"
```

</details>

### event-loop

short version: `el`

description: Event loop.

#### actions

##### add-to

<details><summary>Specify a name, a event loop group, and create a new event loop in the specified group.</summary>

<br>

examples:

```
$ add event-loop el0 to elg elg0
"OK"
```

</details>

##### list

<details><summary>Retrieve names of all event loops in a event loop group.</summary>

<br>

examples:

```
$ list event-loop in event-loop-group elg0
1) "el0"
```

```
$ list-detail event-loop in event-loop-group elg0
1) "el0"
```

</details>

##### remove-from

<details><summary>Remove a event loop from event loop group.</summary>

<br>

examples:

```
$ remove event-loop el0 from event-loop-group elg0
"OK"
```

</details>

### server

short version: `svr`

description: A remote endpoint.

#### actions

##### add-to

<details><summary>Specify name, remote ip:port, weight, and attach the server into the server group.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|address|Remote address, ip:port.|||
|weight|Weight of the server, which will be used by wrr, wlc and source algorithm.|Y|10|

examples:

```
$ add server svr0 to server-group sg0 address 127.0.0.1:6379 weight 10
"OK"
```

</details>

##### list

<details><summary>Retrieve names of all servers in a server group.</summary>

<br>

examples:

```
$ list server in server-group sg0
1) "svr0"
```

</details>

##### list-detail

<details><summary>Retrieve detailed info of all servers in a server group.</summary>

<br>

examples:

```
$ list-detail server in server-group sg0
1) "svr0 -> connect-to 127.0.0.1:6379 weight 10 currently DOWN"
```

</details>

##### update

<details><summary>Change weight of the server.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|weight|Weight of the server, which will be used by wrr, wlc and source algorithm.|Y|not changed|

examples:

```
$ update server svr0 in server-group sg0 weight 11
"OK"
```

</details>

##### remove-from

<details><summary>Remove a server from a server group.</summary>

<br>

examples:

```
$ remove server svr0 from server-group sg0
"OK"
```

</details>

### security-group

short version: `secg`

description: A white/black list, see `security-group-rule` for more info.

#### actions

##### add

<details><summary>Create a security group.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|default|Default: enum {allow, deny}<br>if set to allow, then will allow connection if all rules not match<br>if set to deny, then will deny connection if all rules not match.|||

examples:

```
$ add security-group secg0 default allow
"OK"
```

</details>

##### list

<details><summary>Retrieve names of all security groups.</summary>

<br>

examples:

```
$ list security-group
1) "secg0"
```

</details>

##### list-detail

<details><summary>Retrieve detailed info of all security groups.</summary>

<br>

examples:

```
$ list-detail security-group
1) "secg0 -> default allow"
```

</details>

##### update

<details><summary>Update properties of a security group.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|default|Default: enum {allow, deny}.|||

examples:

```
$ update security-group secg0 default deny
"OK"
```

</details>

##### remove

<details><summary>Remove a security group.</summary>

<br>

examples:

```
$ remove security-group secg0
"OK"
```

</details>

### security-group-rule

short version: `secgr`

description: A rule containing protocol, source network, dest port range and whether to deny.

#### actions

##### add-to

<details><summary>Create a rule in the security group.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|network|A cidr string for checking client ip.|||
|protocol|Enum {TCP, UDP}.|||
|port-range|A tuple of integer for vproxy port, 0 <= first <= second <= 65535.|||
|default|Enum {allow, deny}<br>if set to allow, then will allow the connection if matches<br>if set to deny, then will deny the connection if matches.|||

examples:

```
$ add security-group-rule secgr0 to security-group secg0 network 10.127.0.0/16 protocol TCP port-range 22,22 default allow
"OK"
```

</details>

##### list

<details><summary>Retrieve names of all rules in a security group.</summary>

<br>

examples:

```
$ list security-group-rule in security-group secg0
1) "secgr0"
```

</details>

##### list-detail

<details><summary>Retrieve detailed info of all rules in a security group.</summary>

<br>

examples:

```
$ list-detail security-group-rule in security-group secg0
1) "secgr0 -> allow 10.127.0.0/16 protocol TCP port [22,33]"
```

</details>

##### remove

<details><summary>Remove a rule from a security group.</summary>

<br>

examples:

```
$ remove security-group-rule secgr0 from security-group secg0
"OK"
```

</details>

### cert-key

short version: `ck`

description: Some certificates and one key.

#### actions

##### add

<details><summary>Load certificates and key from file.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|cert|The cert file path. Multiple files are separated with `,`.|||
|key|The key file path.|||

examples:

```
$ add cert-key vproxy.cassite.net cert ~/cert.pem key ~/key.pem
"OK"
```

</details>

##### list

<details><summary>View loaded cert-key resources.</summary>

<br>

examples:

```
$ list cert-key
1) "vproxy.cassite.net"
```

</details>

##### remove

<details><summary>Remove a cert-key resource.</summary>

<br>

examples:

```
$ remove cert-key vproxy.cassite.net
"OK"
```

</details>

### dns-cache

description: The dns record cache. It's a host -> ipv4List, ipv6List map. It can only be accessed from the (default) dns resolver.

#### actions

##### list

<details><summary>Count current cache.</summary>

<br>

examples:

```
$ list dns-cache in resolver (default)
(integer) 1
```

</details>

##### list-detail

<details><summary>List detailed info of dns cache.The return values are:host.ipv4 ip list.ipv6 ip list.</summary>

<br>

examples:

```
$ list-detail dns-cache in resolver (default)
1) 1) "localhost"
   2) 1) "127.0.0.1"
   3) 1) "[0000:0000:0000:0000:0000:0000:0000:0001]"
```

#### remove

<br/>

description: Specify the host and remove the dns cache.

examples:

```
remove dns-cache localhost from resolver (default)
"OK"
```

</details>

### server-sock

short version: `ss`

description: Represents a `ServerSocketChannel`, which binds an ip:port.

#### actions

<details><summary>list</summary>

<br/>

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

</details>

<details><summary>list-detail</summary>

<br/>

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

</details>

### connection

short version: `conn`

description: Represents a `SocketChannel`.

#### actions

<details><summary>list</summary>

<br/>

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

</details>

<details><summary>list-detail</summary>

<details><summary>Specify the host and remove the dns cache.</summary>

<br>

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

#### remove from

<br/>

description: Close the connection, and if the connection is bond to a session, the session will be closed as well.

Supports regexp pattern or plain string:

* if the input starts with `/` and ends with `/`, then it's considered as a regexp.
* otherwise it matches the full string.

examples:

```
remove conn 127.0.0.1:57629/127.0.0.1:16666 from el worker2 in elg worker
"OK"
```

remove conn /.*/ from el worker2 in elg worker
"OK"
```

</details>

### session

short version: `sess`

description: Represents a tuple of connections: the connection from client to lb, and the connection from lb to backend server.

#### actions

<details><summary>list</summary>

<br/>

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

</details>

<details><summary>list-detail</summary>

<br/>

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

#### remove from

<br/>

description: Close a session from lb.

examples:

```
remove sess 127.0.0.1:58713/127.0.0.1:18080->127.0.0.1:58714/127.0.0.1:16666 from tl lb0
"OK"
```

remove sess /127.0.0.1:58713.*/ from tl lb0
"OK"
```

</details>

### bytes-in

short version: `bin`

description: Statistics: bytes flow from remote to local.

#### actions

<details><summary>list</summary>

<br/>

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

</details>

### bytes-out

short version: `bout`

description: Statistics: bytes flow from local to remote.

#### actions

<details><summary>list</summary>

<br/>

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

</details>

### accepted-conn-count

description: Statistics: successfully accpeted connections.

#### actions

<details><summary>list</summary>

<br/>

description: Get history total accepted connection count.

examples:

```
$ list accepted-conn-count in server-sock 127.0.0.1:6380 in tl lb0
(integer) 2
```

</details>

### switch

short version: `sw`

description: A switch for vproxy wrapped vxlan packets.

#### actions

##### add

<details><summary>Create a switch.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|address|Binding udp address of the switch for wrapped vxlan packets.|||
|mac-table-timeout|Timeout for mac table (ms).|Y|300000|
|arp-table-timeout|Timeout for arp table (ms).|Y|14400000|
|event-loop-group|The event loop group used for handling packets.|Y|(worker-elg)|
|security-group|The security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected).|Y|(allow-all)|
|mtu|Default mtu setting for new connected ports.|Y|1500|
|flood|Default flood setting for new connected ports.|Y|allow|

examples:

```
$ add switch sw0 address 0.0.0.0:4789
"OK"
```

</details>

##### list

<details><summary>Get names of switches.</summary>

<br>

examples:

```
$ list switch
1) "sw0"
```

</details>

##### list-detail

<details><summary>Get detailed info of switches.</summary>

<br>

examples:

```
$ list-detail switch
1) "sw0" -> event-loop-group worker bind 0.0.0.0:4789 password p@sSw0rD mac-table-timeout 300000 arp-table-timeout 14400000 bare-vxlan-access (allow-all)
```

</details>

##### update

<details><summary>Update a switch.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|mac-table-timeout|Timeout for mac table (ms).|Y|not changed|
|arp-table-timeout|Timeout for arp table (ms).|Y|not changed|
|security-group|The security group for bare vxlan packets (note: vproxy wrapped encrypted packets won't be affected).|Y|not changed|
|mtu|Default mtu setting for new connected ports, updating it will not affect the existing ones.|Y|not changed|
|flood|Default flood setting for new connected ports, updating it will not affect the existing ones.|Y|not changed|

examples:

```
$ update switch sw0 mac-table-timeout 60000 arp-table-timeout 120000
"OK"
```

</details>

##### remove

<details><summary>Stop and remove a switch.</summary>

<br>

examples:

```
$ remove switch sw0
"OK"
```

</details>

##### add-to

<details><summary>Add a remote switch ref to a local switch. note: use list iface to see these remote switches.</summary>

<br>

flags:

|name|description|opt|default|
|---|---|:---:|:---:|
|no-switch-flag|Do not add switch flag on vxlan packets sent through this iface.|Y||

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|address|The remote switch address.|||

examples:

```
$ add switch sw1 to switch sw0 address 100.64.0.1:18472
"OK"
```

</details>

##### remove-from

<details><summary>Remove a remote switch ref from a local switch.</summary>

<br>

examples:

```
$ remove switch sw1 from switch sw0
"OK"
```

</details>

### vpc

description: A private network.

#### actions

##### add-to

<details><summary>Create a vpc in a switch. the name should be vni of the vpc.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|v4network|The ipv4 network allowed in this vpc.|||
|v6network|The ipv6 network allowed in this vpc.|Y|not allowed|
|annotations|Annotations of the vpc.|Y|{}|

examples:

```
$ add vpc 1314 to switch sw0 v4network 172.16.0.0/16
"OK"
```

</details>

##### list

<details><summary>List existing vpcs in a switch.</summary>

<br>

examples:

```
$ list vpc in switch sw0
1) (integer) 1314
```

</details>

##### list-detail

<details><summary>List detailed info about vpcs in a switch.</summary>

<br>

examples:

```
$ list-detail vpc in switch sw0
1) "1314 -> v4network 172.16.0.0/16"
```

</details>

##### remove-from

<details><summary>Remove a vpc from a switch.</summary>

<br>

examples:

```
$ remote vpc 1314 from switch sw0
"OK"
```

</details>

### iface

description: Connected interfaces.

#### actions

##### list

<details><summary>Count currently connected interfaces in a switch.</summary>

<br>

examples:

```
$ list iface in switch sw0
(integer) 2
```

</details>

##### list-detail

<details><summary>List current connected interfaces in a switch.</summary>

<br>

examples:

```
$ list-detail iface in switch sw0
1) "Iface(192.168.56.2:8472)"
2) "Iface(100.64.0.4:8472)"
```

</details>

##### update

<details><summary>Update interface config.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|mtu|Mtu of this interface.|Y|1500|
|flood|Whether to allow flooding traffic through this interface, allow or deny.|Y|allow|

examples:

```
$ update iface tap:tap0 in switch sw0 mtu 9000 flood allow
"OK"
```

```
$ update iface tun:utun9 in switch sw0 mtu 9000 flood allow
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

</details>

### arp

description: Arp and mac table entries.

#### actions

##### list

<details><summary>Count entries in a vpc.</summary>

<br>

examples:

```
$ list arp in vpc 1314 in switch sw0
(integer) 2
```

</details>

##### list-detail

<details><summary>List arp and mac table entries in a vpc.</summary>

<br>

examples:

```
$ list-detail arp in vpc 1314 in switch sw0
1) "aa:92:96:2f:3b:7d        10.213.0.1             Iface(127.0.0.1:54042)        ARP-TTL:14390        MAC-TTL:299"
2) "fa:e8:aa:6c:45:f4        10.213.0.2             Iface(127.0.0.1:57374)        ARP-TTL:14390        MAC-TTL:299"
```

</details>

### user

description: User in a switch.

#### actions

##### add-to

<details><summary>Add a user to a switch.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|password|Password of the user.|||
|vni|Vni assigned for the user.|||
|mtu|Mtu for the user interface when the user is connected.|Y|mtu setting of the switch|
|flood|Whether the user interface allows flooding traffic.|Y|flood setting of the switch|

examples:

```
$ add user hello to switch sw0 vni 1314 password p@sSw0rD
"OK"
```

</details>

##### list

<details><summary>List user names in a switch.</summary>

<br>

examples:

```
$ list user in switch sw0
1) "hello"
```

</details>

##### list-detail

<details><summary>List all user info in a switch.</summary>

<br>

examples:

```
$ list-detail user in switch sw0
1) "hello" -> vni 1314
```

</details>

##### update

<details><summary>Update user info in a switch.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|mtu|Mtu for the user interface when the user is connected, updating it will not affect connected ones.|Y|not changed|
|flood|Whether the user interface allows flooding traffic, updating it will not affect connected ones.|Y|not changed|

examples:

```
$ update user hello in switch sw0 mtu 1500 flood allow
"OK"
```

</details>

##### remove-from

<details><summary>Remove a user from a switch.</summary>

<br>

examples:

```
$ remove user hello from switch sw0
"OK"
```

</details>

### tap

description: Add/remove a tap device and bind/detach it to/from a switch. The input alias may also be a pattern, see linux tuntap manual. Note: 1) use list iface to see these tap devices, 2) should set -Dvfd=posix or -Dvfd=windows.

#### actions

##### add-to

<details><summary>Add a user to a switch. Note: the result string is the name of the tap device because might be generated.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|vni|Vni of the vpc which the tap device is attached to.|||
|post-script|Post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch).|Y|(empty)|
|mtu|Mtu of this tap device.|Y|mtu setting of the switch|
|flood|Whether the tap device allows flooding traffic.|Y|flood setting of the switch|

examples:

```
$ add tap tap%d to switch sw0 vni 1314
"tap0"
```

</details>

##### remove-from

<details><summary>Remove and close a tap from a switch.</summary>

<br>

examples:

```
$ remove tap tap0 from switch sw0
"OK"
```

</details>

### tun

description: Add/remove a tun device and bind/detach it to/from a switch. The input alias may also be a pattern, see linux tuntap manual. Note: 1) use list iface to see these tun devices, 2) should set -Dvfd=posix.

#### actions

##### add-to

<details><summary>Add a user to a switch. Note: the result string is the name of the tun device because might be generated.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|vni|Vni of the vpc which the tun device is attached to.|||
|mac|Mac address of this tun device. the switch requires l2 layer frames for handling packets.|||
|post-script|Post script. the vproxy will give env variables: VNI, DEV (the generated device name), SWITCH (name of the switch).|Y|(empty)|
|mtu|Mtu of this tun device.|Y|mtu setting of the switch|
|flood|Whether the tun device allows flooding traffic.|Y|flood setting of the switch|

examples:

```
$ add tun tun%d to switch sw0 vni 1314
"tun0"
```

```
$ add tun utun9 to switch sw0 vni 1314
"utun9"
```

</details>

##### remove-from

<details><summary>Remove and close a tun from a switch.</summary>

<br>

examples:

```
$ remove tun tun0 from switch sw0
"OK"
```

</details>

### user-client

short version: `ucli`

description: User client of an encrypted tunnel to remote switch. Note: use list iface to see these clients.

#### actions

##### add-to

<details><summary>Add a user client to a switch.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|password|Password of the user.|||
|vni|Vni which the user is assigned to.|||
|address|Remote switch address to connect to.|||

examples:

```
$ add user-client hello to switch sw0 password p@sSw0rD vni 1314 address 192.168.77.1:18472
"OK"
```

</details>

##### remove-from

<details><summary>Remove a user client from a switch.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|address|Remote switch address the client connected to.|||

examples:

```
$ remove user-client hello from switch sw0 address 192.168.77.1:18472
"OK"
```

</details>

### ip

description: Synthetic ip in a vpc of a switch.

#### actions

##### add-to

<details><summary>Add a synthetic ip to a vpc of a switch.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|mac|Mac address that the ip assigned on.|||

examples:

```
$ add ip 172.16.0.21 to vpc 1314 in switch sw0 mac e2:8b:11:00:00:22
"OK"
```

</details>

##### list

<details><summary>Show synthetic ips in a vpc of a switch.</summary>

<br>

examples:

```
$ list ip in vpc 1314 in switch sw0
1) "172.16.0.21"
2) "[2001:0db8:0000:f101:0000:0000:0000:0002]"
```

</details>

##### list-detail

<details><summary>Show detailed info about synthetic ips in a vpc of a switch.</summary>

<br>

examples:

```
$ list-detail ip in vpc 1314 in switch sw0
1) "172.16.0.21 -> mac e2:8b:11:00:00:22"
2) "[2001:0db8:0000:f101:0000:0000:0000:0002] -> mac e2:8b:11:00:00:33"
```

</details>

##### remove-from

<details><summary>Remove a synthetic ip from a vpc of a switch.</summary>

<br>

examples:

```
$ remove ip 172.16.0.21 from vpc 1314 in switch sw0
"OK"
```

</details>

### route

description: Route rules in a vpc of a switch.

#### actions

##### add-to

<details><summary>Add a route to a vpc of a switch.</summary>

<br>

parameters:

|name|description|opt|default|
|---|---|:---:|---|
|network|Network to be matched.|||
|vni|The vni to send packet to. only one of vni|via can be used.|||
|via|The address to forward the packet to. only one of via|vni can be used.|||

examples:

```
$ add route to172.17 to vpc 1314 in switch sw0 network 172.17.0.0/24 vni 1315
"OK"
```

```
$ add route to172.17 to vpc 1314 in switch sw0 network 172.17.0.0/24 via 172.16.0.1
"OK"
```

</details>

##### list

<details><summary>Show route rule names in a vpc of a switch.</summary>

<br>

examples:

```
$ list route in vpc 1314 in switch sw0
1) "to172.17"
2) "to2001:0db8:0000:f102"
```

</details>

##### list-detail

<details><summary>Show detailed info about route rules in a vpc of a switch.</summary>

<br>

examples:

```
$ list-detail route in vpc 1314 in switch sw0
1) "to172.17 -> network 172.17.0.0/24 vni 1315"
2) "to2001:0db8:0000:f102 -> network [2001:0db8:0000:f102:0000:0000:0000:0000]/64 vni 1315"
```

</details>

##### remove-from

<details><summary>Remove a route rule from a vpc of a switch.</summary>

<br>

examples:

```
$ remove route to172.17 from vpc 1314 in switch sw0
"OK"
```

</details>

## Params

### acceptor-elg

short version: `aelg`

description: Acceptor event loop group.

### event-loop-group

short version: `elg`

description: Event loop group.

### address

short version: `addr`

description: Ip address -> ip:port.

### via

description: The gateway ip for routing.

### upstream

short version: `ups`

description: Upstream.

### in-buffer-size

description: In buffer size.

### out-buffer-size

description: Out buffer size.

### security-group

short version: `secg`

description: Security group.

### timeout

description: Health check timeout.

### period

description: Health check period.

### up

description: Health check up times.

### down

description: Health check down times.

### method

short version: `meth`

description: Method to retrieve a server.

### weight

description: Weight.

### default

description: Enum: allow or deny.

### network

short version: `net`

description: Network: $network/$mask.

### v4network

short version: `v4net`

description: Ipv4 network: $v4network/$mask.

### v6network

short version: `v6net`

description: Ipv6 network: $v6network/$mask.

### protocol

description: For tcp-lb: the application layer protocol, for security-group: the transport layer protocol: tcp or udp.

### annotations

short version: `anno`

description: A string:string json representing metadata for the resource.

### port-range

description: An integer tuple $i,$j.

### service

description: Service name.

### zone

description: Zone name.

### nic

description: Nic name.

### ip-type

description: Ip type: v4 or v6.

### port

description: A port number.

### cert-key

short version: `ck`

description: Cert-key resource.

### cert

description: The certificate file path.

### key

description: The key file path.

### ttl

description: Time to live.

### mac-table-timeout

description: Timeout of mac table in a switch.

### arp-table-timeout

description: Timeout of arp table in a switch.

### password

short version: `pass`

description: Password.

### mac

description: Mac address.

### vni

description: Vni number.

### post-script

description: The script to run after added.

### mtu

description: Max transmission unit.

### flood

description: Flooding traffic.

## Flags

### noipv4

description: do not use ipv4 address. Use the flag with param: address

### noipv6

description: do not use ipv6 address. Use the flag with param: address

### allow-non-backend

description: allow to access non backend endpoints

### deny-non-backend

description: only able to access backend endpoints

### no-switch-flag

description: do not add switch flag on vxlan packet

