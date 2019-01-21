# vproxy

## Intro

VProxy is a zero-dependency TCP Loadbalancer based on Java NIO. The project only requires Java 8 to run.

Clone it, javac it, then everything is ready for running.

## Aim

* Zero dependency: no dependency other than java standard library.
* Simple: keep code simple and clear.
* Modifiable when running: no need to reload for configuration update.
* TCP Loadbalancer: we only support TCP for now.

## Architecture

![architecture](vproxy.jpg)

### Data Plane

Let's start from the bottom.

Everything is based on java nio `Selector`.

We first build the `SelectorEventLoop`. It provides a common callback handler wrapper for `Channel` events. Also the loop can handle time events, which is based on `selector.select(timeout)`.  
You may consider it in the same position as libae in redis.

Then we build `NetEventLoop` based on `SelectorEventLoop`, and provide a few wrapper for `SocketChannel`s, as you can see from the architecture figure. This makes network related coding easy and simple.  
Also, vproxy provide a `RingBuffer` (as util, but used in almost every component), which can write and read at the same time. The network handling is simple: you write into output buffer, then the lib writes that data to channel; when reading is possible, the lib calls your readable callback and you can read data from input buffer.

We start to build the lb part after having these two event loops.

The `Proxy` can accept connections, dispatch the connections on different loops, create connections to some remote endpoints, and then proxy the network data.  
The accept eventloop, handle eventloop (for handling connections), which backend to use, are all configurable and can be changed when running.

`EventLoopWrapper` is only a wrapper for NetEventLoop. It keeps registered channel data for statistics and management. Also it can bind resources, which will be alerted on removal or when event loop ends.

`EventLoopGroup` contains multiple `EventLoopWrapper`. Also it can bind resources, just like `EventLoopWrapper`. It provides a `next()` method to retrive the next running event loop. The method of selecting event loop is always RR.

`ConnectClient` is a client that connects to the remote endpoint then close that connection. It is registered with a callback, which will return whether the connection is ok.

`HealthCheckClient` is a client that connects to remote endpoint periodically, and check whether it's online. It is registered with a handler, which will alert you when the target endpoint is switched to DOWN or UP (the trigger is Edge Trigger).

`ServerGroup` is a group of endpoints, each endpoint is attached with a boolean flag indicating it's currently healthy or not. The `ServerGroup` provides a `next()` method to retrieve the next healthy server. The method of determining which is the "next" is also configurable.

`ServerGroups` is a list of group, it's only a container and do not do IO it self. It also provides a `next()` method, which will run throught all serverGroups and retrieve a healthy server. The method of selecting serverGroup is always RR.

`TcpLB` listen on a port and do loadbalancing. You can create multiple `TcpLB`s if you want to listen on multiple ports.

### Control Plane

VProxy will create a event loop named `ControlEventLoop` for controlling operations. All quick operations will be operated on this event loop, some operations that might take a very long time will be operated on new threads.

VProxy provides you with multiple ways of configuring the vproxy instance: (for now, only `StdIOController` is provided, will add more in the future).

`StdIOController` provide controlling the vproxy process via standard input and output. It just starts a `Scanner` to watch your commands on a new thread. Results or errors or logs will be printed to the stdout or stderr.
