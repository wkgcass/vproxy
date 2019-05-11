# Using application layer protocols

## General

The layer 4 TCP loadbalancer transfers all data from one frontend connection to one backend. This will result in the following major problems:

1. One backend load would be higher than others if the netflow of a single connection is very high but other connections are not busy.
2. RPC is frequently used in the internal network (e.g. idc or vpc). Almost all impl of rpc protocols would create a connection with a few connnections, and won't be closed in normal cases. If using the L4 loadbalancing, the backend rpc services won't be able to scale out.

So `vproxy` defines a set of interfaces which allow users to customize their own application level protocols, and to dispatch frames to different backends in one connection.

Now, `vproxy` already uses the interfaces to construct a built-in protcol for `HTTP/2`. See [here](https://github.com/wkgcass/vproxy/tree/master/src/main/java/net/cassite/vproxy/processor/http2). The core impl is here: [Http2SubContext.java](https://github.com/wkgcass/vproxy/blob/master/src/main/java/net/cassite/vproxy/processor/http2/Http2SubContext.java).

## How to use

When creating `tcp-lb`, specify the application level protocol to use in parameter `protocol ${}`, e.g. `protocol h2`.

Current built in protocols are:

* h2: `http/2`

Input your protocol name which corresponds to your `Processor` when using a customized protocol.

## How to customize protocols

### Example

We provides a processor impl example, see [here](https://github.com/wkgcass/vproxy-customized-application-layer-protocols-example). Here we defined a very simple application level protocol: the first 3 bytes represnets the payload length, and followed by the payload. See example code for more info.

For more complex protocols, you may refer to the built-in http2 impl.

### Interfaces

package [net.cassite.vproxy.processor](https://github.com/wkgcass/vproxy/tree/master/src/main/java/net/cassite/vproxy/processor);

* [ProcessorRegistry](https://github.com/wkgcass/vproxy/blob/master/src/main/java/net/cassite/vproxy/processor/ProcessorRegistry.java) To register customized protocol into the `vproxy`.
* [Processor](https://github.com/wkgcass/vproxy/blob/master/src/main/java/net/cassite/vproxy/processor/Processor.java) The processor of your protocol

### Concepts

* Frontend connection：the connection from client to loadbalancer.
* Backend connection：the connection from loadbalancer to backend.
* lib：refers to `vproxy`.
* ctx：context，processing infomation about (one frontend connection + the derived backend connections).
* subCtx：sub context, processing infomation about one connection.

### Methods of processor interface

The Processor interface defines the following methods:

* `String name()` name of the protocol
* `CTX init()` the context
* `SUB initSub(CTX ctx, int id)` creating a sub context
* `Mode mode(CTX ctx, SUB sub)` the current processing mode，maybe `handle` or `proxy`
* `int len(CTX ctx, SUB sub)` current expected data length
* `ByteArray feed(CTX ctx, SUB sub, ByteArray data)` feed data from the source connection to the processor, and return the data to be sent to the target connection
* `ByteArray produce(CTX ctx, SUB sub)` produce data to the source connection (will only be called on backend connections)
* `void proxyDone(CTX ctx, SUB sub)` inform that the proxy is done
* `int connection(CTX ctx, SUB front)` decide which connection should data be sent to, -1 means to let the lib decide
* `void chosen(CTX ctx, SUB front, SUB sub)` inform which connection the lib chooses
* `ByteArray connected(CTX ctx, SUB sub)` inform that a new connection has established
* `int PROXY_ZERO_COPY_THRESHOLD()` the threshold for performing zero copy

### How the lib works

```python
# 1.when the frontend connection is established
def on_frontend_connection_establish():
  ctx = init() # 2.create the context
  fctx = initSub(ctx, 0) # 3.create the sub context for frontend connection

# 4.when data arrives on the frontend connection
def on_frontend_data():
  len = len(ctx, fctx) # 5.retrieve the length of expected data
  if (mode(ctx, fctx) == 'handle'):
    data = _read(frontend_conn, len) # retrieve data of length `len` from frontend connection
    data = feed(ctx, fctx, data) # 6.send data to processor, and generate data to send to backend
    conn_id = connection(ctx, fctx) # 7.select which connection to send data to
    backend_conn = get_backend_conn(connId)
    _write(backend_conn, data) # send data to backend connection
  else: # mode == 'proxy'
    conn_id = connection(ctx, fctx) # 11.select which connection to send data to
    backend_conn = get_backend_conn(connId)
    if len > PROXY_ZERO_COPY_THRESHOLD():
      _proxy_zero_copy(from=frontend_conn, to=backend_conn, len) # proxy to backend
    else:
      _proxy(from=frontend_conn, to=backend_conn, len) # proxy to backend
    proxyDone(ctx, fctx) # 12.inform that proxy completed

# retrieve the backend connection
def get_backend_conn(conn_id):
  if (conn_id != -1): # if connId is specified
    return _existing_connection(conn_id) # get existing connection

  backend_conn = _get_backend_connection() # retrieve a backend connection
  conn_id = _get_next_conn_id()
  bctx = initSub(ctx, conn_id) # 8.create the sub context for backend connection
  bdata = connected(ctx, bctx) # 9.retrieve data which will be sent to backend
  chosen(ctx, fctx, bctx) # 10.inform the processor that the backend is selected
  _write(backend_conn, bdata) # send data when it's connected

# 13.when data arrives on backend connection
def on_backend_data():
  len = len(ctx, bctx) # 14.retrieve the length of data
  if (mode(ctx, bctx) == 'handle'):
    data = _read(conn, len) # read data with length `len` from backend connection
    data = feed(ctx, bctx, data) # 15.feed processor with data and retrieve the data to be sent to frontend connection
    data2 = produce(ctx, bctx) # 16.retrieve data to be reply to backend
    _write(backend_conn, data2) # reply data to backend
    _write(frontend_conn, data) # send data to frontend connection
  else: # mode == 'proxy'
    if len > PROXY_ZERO_COPY_THRESHOLD():
      _proxy_zero_copy(from=backend_conn, to=frontend_conn, len)
    else:
      _proxy(from=backend_conn, to=frontend_conn, len)
    proxyDone(ctx, bctx) # 17.the proxy is done
```
