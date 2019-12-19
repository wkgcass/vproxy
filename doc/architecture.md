## Architecture

![architecture](https://github.com/wkgcass/vproxy/blob/master/doc_assets/001-vproxy.jpg?raw=true)

### Data Plane

Let's start from the bottom.

Everything is based on `FD` and `Selector`.

#### FD

FD is an abstraction for jdk channel or os fds. With the help of FDs, we can easily change the whole network stack without touching the upper level code. For example you can use (almost) all functionalities when you switch to the `F-Stack` fd implementation.

Also it makes it easy to write ARQ protocols based on UDP, and wrap them into `TCP-like` API, and plug into the vproxy system. For example you can use the KCP FDs impl just like using a normal TCP channel.

#### SelectorEventLoop

We first built the `SelectorEventLoop`. It provides a common callback handler wrapper for `Channel` events. Also the loop can handle time events, which is based on `selector.select(timeout)`.  
You may consider it in the same position as libae in redis.

#### NetEventLoop

Then we built `NetEventLoop` based on `SelectorEventLoop`, and provided a few wrappers for `SocketChannel`s, as you can see from the architecture figure. This makes network related coding easy and simple.  
Also, vproxy provides a `RingBuffer` (in util package, used in almost every component), which can write and read at the same time. The network handling is simple: you write into output buffer, then the lib writes that data to channel; when reading is possible, the lib calls your readable callback and you can read data from the input buffer.

We start to build the lb part after having these two event loops.

#### Proxy

The `Proxy` can accept connections, dispatch the connections on different loops, create connections to some remote endpoints, and then proxy the network data.  
The accept eventloop, handle eventloop (for handling connections), which backend to use, are all configurable and can be changed when running.

#### EventLoopWrapper

`EventLoopWrapper` is nothing more than a wrapper for NetEventLoop. It keeps registered channel data for statistics and management. Also it can bind resources, which will be alerted on removal or when event loop ends.

#### EventLoopGroup

`EventLoopGroup` contains multiple `EventLoopWrapper`. Also it can bind resources, just like `EventLoopWrapper`. It provides a `next()` method to retrive the next running event loop. The method of selecting event loop is always RR.

#### ConnectClient

`ConnectClient` is a client that connects to the remote endpoint then closes that connection. It is registered with a callback, which will alert you whether the connection is ok or failed or timed-out.

#### HealthCheckClient

`HealthCheckClient` is a client that connects to remote endpoint periodically, and check whether that endpoint is online. It is registered with a handler, which will alert you when the target endpoint is switched to DOWN or UP (the trigger is Edge Trigger).

#### ServerGroup

`ServerGroup` is a group of endpoints, each endpoint is attached with a boolean flag indicating it's currently healthy or not. The `ServerGroup` provides a `next()` method to retrieve the next healthy server. The method of determining which is the "next" is configurable.

#### Upstream

`Upstream` is a list of groups, each group is assigned with a weight. It provides a `next()` method, which accepts a `hint` and it will select the most corresponding serverGroup, or will choose a default one if no `hint` provided or nothing matches. The method of selecting serverGroup is always WRR, and it doesn't affect how `ServerGroup` selects server.

#### TcpLB and Socks5Server

These are the main functionalities that vproxy main program provides.

`TcpLB` listens on a port and does loadbalancing for TCP based protocols (e.g. HTTP). You can create multiple `TcpLB`s if you want to listen on multiple ports. `Socks5Server` is almost the same as `TcpLB` but it runs socks5 protocol and proxies netflow to client specified backend.

### Control Plane

VProxy will create a event loop named `ControlEventLoop` for controlling operations. All quick operations will be operated on this event loop, some operations that might take a very long time will be operated on new threads.

VProxy provides you with multiple ways of configuring the vproxy instance.

#### StdIOController

`StdIOController` provides the way of controlling the vproxy process via standard input and output. It simply starts a `Scanner` to watch your commands on a new thread. Results, errors or logs will be printed to the stdout or stderr.

#### RESPController

`RESPController` listens on a port and uses the REdis Serialization Protocol for transporting commands and results. You can use `redis-cli` to manage the vproxy instance.

#### HTTPController

`HTTPController` creates an HTTP server that exposes RESTful json api to manage the vproxy instance.

### Service Mesh

VProxy provides the ability of service discovery and can act as a sidecar.  
You may use the combination of `TcpLB`, `Socks5Server`, `SmartGroupDelegate` and `SmartNodeDelegate` to build any role inside a mesh.

### Library

With all above functionalities, vproxy wraps some of them and provides libraries with light weight API.

### Application

Besides acting as a loadbalancer, vproxy provides some other network related tools, for example you may use the WebSocksProxyAgent/Server to build a tunnel through fireware.
