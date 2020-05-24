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
7. [Kubernetes](#k8s): use vproxy in a k8s cluster.

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

The vproxy will automatically load the last saved file on startup.

#### 3.2. startup argument

Use `load ${filename}` to load a configuration file when the vproxy instance starts:

e.g.

```
java vproxyapp.app.Main load ~/vproxy.conf
```

> Multiple config files can be specified at the same time, they will be loaded one by one.

#### 3.3. system call command

Start the vproxy instance:

```
java vproxyapp.app.Main
```

Then type in:

```
> System call: save ~/vproxy.conf             --- saves the current config into a file
> System call: load ~/vproxy.conf             --- loads config from a file
```

> You may use `noLoadLast` to forbid loading config on startup.  
> You may use `noSave` to disable the ability of saving files (regardless of auto or manual saving).

<div id="stdio"></div>

## 4. Use StdIOController

Start the vproxy instance:

```
java vproxyapp.app.Main
```

Then the StdIOController starts by default, you can type in commands directly through console.

It's recommended to start vproxy instance in `tmux` or `screen` if you rely on the StdIOController.

> You may use `noStdIOController` to disable StdIOController.

<div id="resp"></div>

## 5. Use RESPController

`RESPController` listens on a port and uses the REdis Serialization Protocol for transporting commands and results.  
With the controller, you can use `redis-cli` to operate the vproxy instance.

```
redis-cli -p 16309 -a m1paSsw0rd
127.0.0.1:16309> man
```

> NOTE: `redis-cli` traps `help` command and prints redis help message.  
> NOTE: So we provided a new command named `man` instead, to retrieve vproxy help message.  
> NOTE: For safety concern, not all `System call:` commands are not allowed in RESPController.
> NOTE: You can use add start argument flag `allowSystemCallInNonStdIOController` to enable system call commands for RESPController

The `resp-controller` is automatically launched on startup and listens to `16309` with password `123456`.  
You may configure the RESPController on startup or using a command in StdIOController.

#### 5.1 startup argument

Use `resp-controller ${address} ${password}` arguments to start the RESPController.

e.g.

```
java vproxyapp.app.Main resp-controller 0.0.0.0:16309 m1paSsw0rd
```

#### 5.2. system call command

Start the vproxy instance:

```
java vproxyapp.app.Main
```

To create a RESPController, you can type in:

```
> System call: add resp-controller ${name} address ${host:port} password ${pass}
```

To list existing RESPController, you can type in:

```
> System call: list-detail resp-controller
resp-controller	127.0.0.1:16309              ---- this is response
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
curl http://127.0.0.1:18776/healthz
```

#### 6.1 startup argument

Use `http-controller ${address}` arguments to start the HTTPController

e.g.

```
java vproxyapp.app.Main http-controller 0.0.0.0:18776
```

#### 6.2. system call command

Start the vproxy instance:

```
java vproxyapp.app.Main
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

<div id="k8s"></div>

## 7. Kubernetes

Use Service and vproxy CRD to implement Gateways, Sidecars, etc.

Please visit [vpctl](https://github.com/vproxy-tools/vpctl) for more detail.
