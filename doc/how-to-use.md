# How to use

Use `help` to see available arguments before starting the vproxy instance.  
After started, inputting `help` or `man` into the instance will give you a list of commands.

There are multiple ways of using vproxy:

* Config file: load a pre configured file when starting or when running.
* StdIOController: type in commands into vproxy and get messages from std-out.
* RESPController: use `redis-cli` or `telnet` to operate the vproxy instance.
* Service Mesh: let the nodes in the cluster to automatically find each other and handle network traffic.

## 1. Config file

VProxy configuration is a text file, each line is a vproxy command.  
The vproxy instance will parse all lines then run all commands one by one.  
This has the same effect as copying each line and pasting the lines into console.

See [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md) for detailed command document.

There are 3 ways of using a config file:

#### 1.1. last auto saved config

The vproxy instance saves current config to `~/.vproxy.last` for every hour.  
The config will also be saved when the process got `sigint`, `sighup` or manually shutdown via controller.

If you start vproxy instance without a `load` argument, the last saved config will be loaded.

Generally, you only need to configure once and don't have to worry about the config file any more.

#### 1.2. startup argument

Use `load ${filename}` to load a configuration file when the vproxy instance starts:

e.g.

```
java net.cassite.vproxy.app.Main load ~/vproxy.conf
```

> Multiple config files can be specified, will be executed in parallel.  
> Also, arguments in different categories can be combined, e.g. you can specify `load ...` and `resp-controller ... ...` at the same time.

#### 1.3. system call command

Start the vproxy instance:

```
java net.cassite.vproxy.app.Main
```

Then type in:

```
> System call: save ~/vproxy.conf             --- saves the current config into a file
> System call: load ~/vproxy.conf             --- loads config from a file
```

## 2. Use StdIOController

Start the vproxy instance:

```
java net.cassite.vproxy.app.Main
```

Then the StdIOController starts, you can type in commands via standard input.

It's recommended to start vproxy instance via tmux or screen if you rely on the StdIOController.

## 3. Use RESPController

`RESPController` listens on a port and uses the REdis Serialization Protocol for transporting commands and results.  
With the controller, you can use `redis-cli` to operate the vproxy instance.

> NOTE: `redis-cli` traps `help` command and prints redis help message.  
> NOTE: So we provided a new command named `man` instead, to retrieve vproxy help message.  
> NOTE: For safety concern, not all `System call:` commands are not allowed in RESPController.
> NOTE: You can use add start argument flag `allowSystemCallInNonStdIOController` to enable system call commands for RESPController

You can start RESPController on startup or using a command in StdIOController.

#### 3.1 startup argument

Use `resp-controller ${address} ${password}` arguments to start the RESPController.

e.g.
```
java net.cassite.vproxy.app.Main resp-controller 0.0.0.0:16379 m1paSsw0rd
```

then you can use `redis-cli` to connect to the vproxy instance.

```
redis-cli -p 16379 -a m1paSsw0rd
127.0.0.1:16379> man
```

#### 3.2. system call command

Start the vproxy instance:

```
java net.cassite.vproxy.app.Main
```

To create a RESPController, you can type in:

```
> System call: add resp-controller ${name} address ${host:port} password ${pass}
```

To list existing RESPController, you can type in:

```
> System call: list-detail resp-controller
resp-controller	127.0.0.1:16379              ---- this is response
>
```

To stop a RESPController, you can type in:

```
> System call: remove resp-controller ${name}
(done)                                       ---- this is response
>
```

## 4. Service Mesh

Specify the service mesh config file when starting:

```
java net.cassite.vproxy.app.Main serviceMeshConfig $path_to_config
```

When service mesh config is specified, the process launches into service mesh mode.  

There are two roles provided by vproxy.

* sidecar: Deployed on application host. One sidecar per host. The user application should use socks5 and use domain names to request cluster services, traffic will be automatically directed to correct endpoints. And user app should listen on localhost, the sidecar will help export the service.
* smart-lb-group: Used as a tcp loadbalancer. The lb will automatically learn node changes in the cluster, and add or remove nodes in backend list.

The user application should use any kind of redis client to let sidecar know what service is running locally.

Use the following redis command to add/check/remove services of a sidecar.  
They behave just like redis SET commands:

```
sadd service $your_service_domain:$protocol_port:$local_port
# return 1 or 0

smembers service
# return a list of services you just added

srem service $your_service_domain:$protocol_port:$local_port
# return 1 or 0
```

User app is recommended to:

1. Use socks5 and domain to make requests, domains should be resolved on the proxy(sidecar) side.
2. Use redis client to register the service when just the app just launched.
3. The service name should be `domain:protocol_port`, e.g. `myservice.com:80`, requesting urls are the same, e.g. `http://myservice.com`.
4. Use redis client to deregister the service and wait for traffic to end before the app stops.
5. Note that: external traffic should not go through the sidecar(socks5 proxy) (because the vproxy network does not know how to make requests to external resources).

## 5. Example and Explanation

### Config file

Create a file, input the following:

```
add server-groups sgs0
add tcp-lb lb0 addr 127.0.0.1:8899 server-groups sgs0
add server-group sg0 timeout 1000 period 3000 up 4 down 5 method wrr
add server-group sg0 to server-groups sgs0 weight 10
add server s0 to server-group sg0 address 127.0.0.1:12345 weight 10
```

and save it, perhaps you can save it into `~/vproxy.conf`.

Start the application via `net.cassite.vproxy.app.Main`

```
java net.cassite.vproxy.app.Main load ~/vproxy.conf
```

Then everything is done.

### Use redis-cli

You can create a `RESPController` via stdIO, then all commands will be available in your `redis-cli` client.

Input the following command in your vproxy application via standard input:

```
System call: add resp-controller r0 addr 0.0.0.0:16379 pass 123456
```

which creates a `RESPController` instance named `r0` listens on `0.0.0.0:16379` and has password `123456`.

Then from any endpoint who has access, you can run this to operate on the vproxy:

```
redis-cli -p 16379 -h $THE_VPROXY_HOST_IP_ADDRESS -a 123456 [$YOU_CAN_ALSO_DIRECTLY_RUN_COMMANDS_HERE]
```

You should know that the `help` is trapped by `redis-cli` (which will return redis-cli's help message), so we give a NEW command named `man`, it will return the same message as using `help` in stdIO.

### Detailed Phases via stdio or redis-cli, Step by Step

To create a tcp loadbalancer, you can:

1. start the application via `net.cassite.vproxy.app.Main`
2. type in the following commands or run from the redis-cli:
3. `add server-groups sgs0`  
    which creates a serverGroups named `sgs0`. The serverGroups is a resource that contains multiple server groups.
4. `add tcp-lb lb0 addr 127.0.0.1:8899 server-groups sgs0`  
    which creates a tcp loadbalancer named `lb0`, using also `elg0` as its worker event loop group. the lb listens on `127.0.0.1:8899`, using `sgs0` as it's backend server groups.

Now the lb is running, you can telnet, however there are no valid backends, so the connection is closed instantly.

To add a backend, you can:

1. `add server-group sg0 timeout 1000 period 3000 up 4 down 5 method wrr`  
    which creates a server group named `sg0`; the health check configurations are: check timeout is 1 second, check every 3 seconds, consider the server UP when got 4 successful checks and consider the server DOWN when got 5 failed checks; the method of retrieving server from this group is `wrr`
2. `add server-group sg0 to server-groups sgs0 weight 10`  
    which adds the server group `sg0` into serverGroups `sgs0`. Adding `sg0` to `sgs0` is because the tcp-lb is using `sgs0` as its backend server groups
3. `add server s0 to server-group sg0 address 127.0.0.1:12345 weight 10`  
    which adds a new server named `s0` into server group `sg0`, the weight of the server in this group is `10`

You may expect a log telling you that the server you just added is turned to UP in a few seconds. Then the loadbalancer is ready for connections.

### Explanation

VProxy provides you with full control of inside components.  
As a result, the configuration is a little different from what you may have thought.

VProxy has a very simple configuration syntax.

```
$action $resource-type [$resource-alias] [in $resource-type $resource-alias [in ...]] [to/from $resource-$type $resource-alias] $param-key $param-value $flag
```

Here's an example:

```
add server myserver0 to server-group group0 address 127.0.0.1:12345 weight 10
```

which says that: I want to add a server named `myserver0` into server group `group0`, whose address is `127.0.0.1:12345`, the server's weight in this group is set to `10`.

You can use `help` command to check all available resources and params. e.g.

```
> help
> man
> man tcp-lb
```

VProxy does not provide configuration like nginx or haproxy, it looks more like ipvsadm. You can have full control of all low level components such as threads and event loops. Also you can modify all components during the runtime without a reload.
