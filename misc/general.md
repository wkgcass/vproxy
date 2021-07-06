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
