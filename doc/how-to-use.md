# How to use

Use `help` to see available arguments before starting the vproxy instance.  
After started, inputting `help` or `man` into the instance will give you a list of commands.

There are multiple ways of using vproxy:

1. [Simple mode](#simple): start a simple loadbalancer in one line.
2. [<font color="green">**vpctl** (recommended)</font>](#vpctl): a standalone application used to control the vproxy instance with yaml configurations.
3. [Config file](#config): load a pre configured file containing commands when starting or when running.
4. [StdIOController](#stdio): type in commands into vproxy and get messages from std-out.
5. [RESPController](#resp): use `redis-cli` or `telnet` to operate the vproxy instance.
6. [HTTPController](#http): control the vproxy instance via http (maybe using `curl`).
7. [Service Mesh](#discovery): let the nodes in the cluster to automatically find each other and handle network traffic.

<div id="simple"></div>

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

<div id="vpctl"></div>

## <font color="green">2. vpctl</font>

To get the `vpctl` code and binary, visit [here](https://github.com/vproxy-tools/vpctl). The configuration examples are listed there as well.

To use the `vpctl`, you need to launch the http-controller. The vpctl uses http apis to control the vproxy instance.

You may launch the http-controller on startup.

```
java -jar vproxy.jar http-controller 127.0.0.1:18776
```

or see other ways in the document for [HTTPController](#http).

Use one command to apply all your configuration:

```
vpctl apply -f my.cnf.yaml
```

Check configuration using `get` command:

e.g.

```
vpctl get TcpLb
```

```
vpctl get DnsServer dns0 -o yaml
```

<div id="config"></div>

## 3. Config file

VProxy configuration is a text file, each line is a vproxy command.  
The vproxy instance will parse all lines then run all commands one by one.  
This has the same effect as copying each line and pasting the lines into console.

See [command.md](https://github.com/wkgcass/vproxy/blob/master/doc/command.md) for detailed command document.

There are 3 ways of using a config file:

#### 3.1. last auto saved config

The vproxy instance saves current config to `~/.vproxy.last` for every hour.  
The config will also be saved when the process got `sigint`, `sighup` or manually shutdown via controller.

If you start vproxy instance without a `load` argument, the last saved config will be loaded.

Generally, you only need to configure once and don't have to worry about the config file any more.

#### 3.2. startup argument

Use `load ${filename}` to load a configuration file when the vproxy instance starts:

e.g.

```
java vproxy.app.Main load ~/vproxy.conf
```

> Multiple config files can be specified, will be executed in parallel.  
> Also, arguments in different categories can be combined, e.g. you can specify `load ...` and `resp-controller ... ...` at the same time.

#### 3.3. system call command

Start the vproxy instance:

```
java vproxy.app.Main
```

Then type in:

```
> System call: save ~/vproxy.conf             --- saves the current config into a file
> System call: load ~/vproxy.conf             --- loads config from a file
```

<div id="stdio"></div>

## 4. Use StdIOController

Start the vproxy instance:

```
java vproxy.app.Main
```

Then the StdIOController starts, you can type in commands via standard input.

It's recommended to start vproxy instance via tmux or screen if you rely on the StdIOController.

<div id="resp"></div>

## 5. Use RESPController

`RESPController` listens on a port and uses the REdis Serialization Protocol for transporting commands and results.  
With the controller, you can use `redis-cli` to operate the vproxy instance.

```
redis-cli -p 16379 -a m1paSsw0rd
127.0.0.1:16379> man
```

> NOTE: `redis-cli` traps `help` command and prints redis help message.  
> NOTE: So we provided a new command named `man` instead, to retrieve vproxy help message.  
> NOTE: For safety concern, not all `System call:` commands are not allowed in RESPController.
> NOTE: You can use add start argument flag `allowSystemCallInNonStdIOController` to enable system call commands for RESPController

You can start RESPController on startup or using a command in StdIOController.

#### 5.1 startup argument

Use `resp-controller ${address} ${password}` arguments to start the RESPController.

e.g.
```
java vproxy.app.Main resp-controller 0.0.0.0:16379 m1paSsw0rd
```

#### 5.2. system call command

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

<div id="http"></div>

## 6. Use HTTPController

`HTTPController` listens on a port and provides http restful api to control the vproxy instance.

```
curl http://127.0.0.1:18776/api/v1/module/tcp-lb
```

#### 6.1 startup argument

Use `http-controller ${address}` arguments to start the HTTPController

e.g.

```
java vproxy.app.Main http-controller 0.0.0.0:18776
```

#### 6.2. system call command

Start the vproxy instance:

```
java vproxy.app.Main
```

To create a HTTPController, you can type in:

```
> System call: add http-controller ${name} address ${host:port}
```

To list existing HTTPController, you can type in:

```
> System call: list-detail http-controller
http-controller	0.0.0.0:18776              ---- this is response
>
```

To stop a HTTPController, you can type in:

```
> System call: remove http-controller ${name}
(done)                                       ---- this is response
>
```

#### 6.3. api doc

See the api [doc](https://github.com/wkgcass/vproxy/blob/master/doc/api.yaml) in swagger 2.0 format.

<div id="discovery"></div>

## 7 . Auto Node Discovery

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
