# How to use

Use `help` to see available arguments before starting the vproxy instance.  
After started, inputting `help` or `man` into the instance will give you a list of commands.

There are multiple ways of using vproxy:

* Config file: load a pre configured file when starting or when running.
* StdIOController: type in commands into vproxy and get messages from std-out.
* RESPController: use `redis-cli` or `telnet` to operate the vproxy instance.

## Config file

VProxy configuration is a text file, each line is a vproxy command.  
The vproxy instance will parse all lines then run all commands one by one.  
This has the same effect as copying each line and pasting the lines into console.

There are two ways of using a config file:

#### startup argument

Use `load ${filename}` to load a configuration file when the vproxy instance starts:

e.g.

```
java net.cassite.vproxy.app.Main load ~/vproxy.conf
```

> Multiple config files can be specified, will be executed in parallel.  
> Also, arguments in different categories can be combined, e.g. you can specify `load ...` and `resp-controller ... ...` at the same time.

#### system call command

Start the vproxy instance:

```
java net.cassite.vproxy.app.Main
```

Then type in:

```
> System call: load ~/vproxy.conf
```

## Use StdIOController

Start the vproxy instance:

```
java net.cassite.vproxy.app.Main
```

Then the StdIOController starts, you can type in commands via standard input.

It's recommended to start vproxy instance via tmux or screen.

## RESPController

`RESPController` listens on a port and uses the REdis Serialization Protocol for transporting commands and results.  
With the controller, you can use `redis-cli` to operate the vproxy instance.

> NOTE: `redis-cli` traps `help` command and prints redis help message.  
> NOTE: So we provided a new command named `man` instead, to retrieve vproxy help message.  
> NOTE: For safety concern, `System call` commands are not allowed in RESPController.

You can start RESPController on startup or using a command in StdIOController.

#### startup argument

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

#### system call command

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
