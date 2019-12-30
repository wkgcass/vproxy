# How to use

Use `help` to see available arguments before starting the vproxy instance.  
After started, inputting `help` or `man` into the instance will give you a list of commands.

There are multiple ways of using vproxy:

* Simple mode: start a simple loadbalancer in one line.
* Config file: load a pre configured file when starting or when running.
* StdIOController: type in commands into vproxy and get messages from std-out.
* RESPController: use `redis-cli` or `telnet` to operate the vproxy instance.
* Service Mesh: let the nodes in the cluster to automatically find each other and handle network traffic.

## 1. Simple mode

You can start a simple loadbalancer in one command:

e.g.

```
java -Deploy=Simple -jar vproxy.jar \
                bind 8888 \
                backend 127.0.0.1:80,127.0.0.1:8080 \
                ssl ~/cert.pem ~/rsa.pem \
                protocol http
```

which listens on `8888`, using protocol http(s), tls certificate is in `~/cert.pem`, key is in `~/rsa.pem`, forwarding netflow to `127.0.0.1:80` and `127.0.0.1:8080`.

You can use `gen` to generate config corresponding to your arguments, then see chapter `Config file` for more info.

## 2. Config file

VProxy configuration is a text file, each line is a vproxy command.  
The vproxy instance will parse all lines then run all commands one by one.  
This has the same effect as copying each line and pasting the lines into console.

See [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md) for detailed command document.

There are 3 ways of using a config file:

#### 2.1. last auto saved config

The vproxy instance saves current config to `~/.vproxy.last` for every hour.  
The config will also be saved when the process got `sigint`, `sighup` or manually shutdown via controller.

If you start vproxy instance without a `load` argument, the last saved config will be loaded.

Generally, you only need to configure once and don't have to worry about the config file any more.

#### 2.2. startup argument

Use `load ${filename}` to load a configuration file when the vproxy instance starts:

e.g.

```
java vproxy.app.Main load ~/vproxy.conf
```

> Multiple config files can be specified, will be executed in parallel.  
> Also, arguments in different categories can be combined, e.g. you can specify `load ...` and `resp-controller ... ...` at the same time.

#### 2.3. system call command

Start the vproxy instance:

```
java vproxy.app.Main
```

Then type in:

```
> System call: save ~/vproxy.conf             --- saves the current config into a file
> System call: load ~/vproxy.conf             --- loads config from a file
```

## 3. Use StdIOController

Start the vproxy instance:

```
java vproxy.app.Main
```

Then the StdIOController starts, you can type in commands via standard input.

It's recommended to start vproxy instance via tmux or screen if you rely on the StdIOController.

## 4. Use RESPController

`RESPController` listens on a port and uses the REdis Serialization Protocol for transporting commands and results.  
With the controller, you can use `redis-cli` to operate the vproxy instance.

> NOTE: `redis-cli` traps `help` command and prints redis help message.  
> NOTE: So we provided a new command named `man` instead, to retrieve vproxy help message.  
> NOTE: For safety concern, not all `System call:` commands are not allowed in RESPController.
> NOTE: You can use add start argument flag `allowSystemCallInNonStdIOController` to enable system call commands for RESPController

You can start RESPController on startup or using a command in StdIOController.

#### 4.1 startup argument

Use `resp-controller ${address} ${password}` arguments to start the RESPController.

e.g.
```
java vproxy.app.Main resp-controller 0.0.0.0:16379 m1paSsw0rd
```

then you can use `redis-cli` to connect to the vproxy instance.

```
redis-cli -p 16379 -a m1paSsw0rd
127.0.0.1:16379> man
```

#### 4.2. system call command

Start the vproxy instance:

```
java vproxy.app.Main
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

## 5. Auto Node Discovery

Specify the discovery config file when starting:

```
java vproxy.app.Main [discoveryConfig $path_to_config]
```

If discovery config not specified, the vproxy instance will load a default config.  
The default config can work well if you have only one nic other than loopback (e.g. eth0), otherwise you may need to specify the configuration file.

There are two modules related to discovery.

* smart-group-delegate: watches the discovery network for node changes, and update the handled server-group resource.
* smart-node-delegate: register a node into the discovery network for others to know.

The user application may use an http client to manipulate the vproxy configuration.

For example: you can register/deregister a node using the http request to http-controller:

```
POST /api/v1/module/smart-node-delegate
{
  "name": "my-test-service",
  "service": "my-service,
  "zone": "test",
  "nic": "eth0",
  "exposedPort": 8080
}
respond 204 for success

DELETE /api/v1/module/smart-node-delegate/my-test-service
respond 204 for success
```

Or you may check the service list registered on the vproxy instance:

```
GET /api/v1/module/smart-node-delegate
```

You may refer to example code in [service-mesh-example.md](https://github.com/wkgcass/vproxy/blob/master/doc/service-mesh-example.md).  
A client helps you register nodes is also provided in the example.

## 6. Example and Explanation

### Config file

Create a file, input the following:

```
add upstream ups0
add tcp-lb lb0 addr 127.0.0.1:8899 upstream ups0
add server-group sg0 timeout 1000 period 3000 up 4 down 5 method wrr
add server-group sg0 to upstream ups0 weight 10
add server s0 to server-group sg0 address 127.0.0.1:12345 weight 10
```

and save it, you may save it into `~/vproxy.conf`.

Start the application via `vproxy.app.Main`

```
java vproxy.app.Main load ~/vproxy.conf
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

1. start the application via `vproxy.app.Main`
2. type in the following commands or run from the redis-cli:
3. `add upstream ups0`  
    which creates an upstream named `ups0`. The upstream is a resource that contains multiple `ServerGroup` resources.
4. `add tcp-lb lb0 addr 127.0.0.1:8899 upstream ups0`  
    which creates a tcp loadbalancer named `lb0`, using also `elg0` as its worker event loop group. the lb listens on `127.0.0.1:8899`, using `ups0` as it's backend upstream.

Now the lb is running, you can telnet, however there are no valid backend, so the connection is closed instantly.

To add a backend, you can:

1. `add server-group sg0 timeout 1000 period 3000 up 4 down 5 method wrr`  
    which creates a server group named `sg0`; the health check configurations are: check timeout is 1 second, check every 3 seconds, consider the server UP when got 4 successful checks and consider the server DOWN when got 5 failed checks; the method of retrieving server from this group is `wrr`
2. `add server-group sg0 to upstream ups0 weight 10`  
    which adds the server group `sg0` into upstream `ups0`. Adding `sg0` to `ups0` is because the tcp-lb is using `ups0` as its backend
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
