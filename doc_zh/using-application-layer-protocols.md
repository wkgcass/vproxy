# 使用应用层协议

## 综述

纯四层的TCP负载均衡将一条前端连接的所有流量转发到同一个后端。这会引发如下几个主要问题：

1. 如果某一连接的流量非常高，但其他连接的流量较低，则会导致某一特定后端的负载升高。
2. 内部网络经常使用rpc，几乎所有rpc协议的实现都会开一个带有少量连接的连接池，并且正常情况下连接不会关闭。如果使用四层负载均衡，rpc服务将无法进行水平扩容。

所以`vproxy`定义了一套接口，允许用户自定义应用层协议，可以自由地将不同的frame分发至不同的后端。

目前，`vproxy`已经使用这套接口，在内部预置了`HTTP/2`,`http/1.x`,`dubbo`,`thrift (framed)`协议的frame分发。  
实际上，这套接口是为了`HTTP/2`协议而提供的，`HTTP/2`的processor使用了这套接口提供的所有功能。详见[这里](https://github.com/wkgcass/vproxy/tree/master/src/main/java/vproxy/processor/http2)，主要实现放在这里：[Http2SubContext.java](https://github.com/wkgcass/vproxy/blob/master/src/main/java/vproxy/processor/http2/Http2SubContext.java)。  
相比而言，`dubbo`和`thrift (framed)`处理器实现要简单的多。而`http/1.x`应用了这套接口的另一种使用姿势。

## 使用方式

创建`tcp-lb`时，在参数中使用`protocol ${}`指定需要使用的应用层协议。

目前内建支持的应用层协议有：

* http: 自适应的`h2`和`http/1.x`
* h2: `http/2`负载均衡
* http/1.x: `http/1.1`和`http/1.0`
* dubbo: 阿里的dubbo rpc
* framed-int32: 可用于framed thrift，它使用32位int值来表示长度

使用自定义协议时，只需填入你在`Processor`中规定的协议名称即可

## 如何自定义协议

### 例子

我们提供了一个processor实现的例子，见[这里](https://github.com/wkgcass/vproxy-customized-application-layer-protocols-example)。这里定义了一个非常简单的应用层协议：前3个字节表示payload长度，后面紧跟payload，请参考具体的代码。

对于比较复杂的协议，可以参考内置的http2的实现而非例子。

为了让vproxy读取你的处理器，你可以使用模块化`module-info`中的`provides ... with ...`语句，也可以使用传统的`META-INF/services/vproxy.processor.ProcessorRegistry`。

当上述两者皆不可用时（比如把所有东西打成了一个大fat jar时），你可以调用`DefaultProcessorRegistry.getInstance().register(processor)`来注册你的处理器。

### 接口

package [vproxy.processor](https://github.com/wkgcass/vproxy/tree/master/src/main/java/vproxy/processor);

* [ProcessorRegistry](https://github.com/wkgcass/vproxy/blob/master/src/main/java/vproxy/processor/ProcessorRegistry.java) 用于将自定义的协议实现注册到`vproxy`中。
* [Processor](https://github.com/wkgcass/vproxy/blob/master/src/main/java/vproxy/processor/Processor.java) 协议处理器。

### 概念

* 前端连接：客户端发往负载均衡的连接
* 后端连接：负载均衡发往后端的连接
* lib：表示`vproxy`
* ctx：上下文，（一个前端连接 + 这个前端连接所派生的后端连接）的处理信息
* subCtx：子上下文，单个连接对应的处理信息

### Processor接口方法

Processor接口定义了如下方法：

* `String name()` 协议名称
* `String[] alpn()` 使用ssl时支持的alpn字符串，越靠前越优先
* `CTX init()` 创建上下文对象
* `SUB initSub(CTX ctx, int id)` 创建子上下文
* `Mode mode(CTX ctx, SUB sub)` 当前处理模式，可以是`handle`或是`proxy`
* `boolean expectNewFrame(CTX ctx, SUB sub)` 处理器需要处理新的frame，换句话说也就是之前的frame已经全部处理完了
* `int len(CTX ctx, SUB sub)` 当前期望处理的长度
* `ByteArray feed(CTX ctx, SUB sub, ByteArray data)` 给处理器传入需要的源连接数据，并产生一组数据发往目标连接
* `ByteArray produce(CTX ctx, SUB sub)` 产生一组数据回应源连接（仅针对后端连接会调用该方法）
* `void proxyDone(CTX ctx, SUB sub)` 指示代理已完成
* `int connection(CTX ctx, SUB front)` 获取应当将数据转发给哪条连接，-1表示由lib分配一条连接
* `void chosen(CTX ctx, SUB front, SUB sub)` 指示lib分配选中的连接
* `ByteArray connected(CTX ctx, SUB sub)` 指示连接已建立，并生存一组需要立即发往该连接的数据
* `int PROXY_ZERO_COPY_THRESHOLD()` 零拷贝阈值

### 执行过程

伪代码描述执行过程：

```python
# 1.当前端连接建立时
def on_frontend_connection_establish():
  ctx = init() # 2.创建上下文
  fctx = initSub(ctx, 0) # 3.创建前端连接的子上下文

# 4.前端连接有数据时
def on_frontend_data():
  len = len(ctx, fctx) # 5.获取所需数据长度
  if (mode(ctx, fctx) == 'handle'):
    data = _read(frontend_conn, len) # 从前端连接获取长度为len的数据
    data = feed(ctx, fctx, data) # 6.将数据传入处理器，并生成需要发往后端的数据
    conn_id = connection(ctx, fctx) # 7.选择要发往哪个后端连接
    backend_conn = get_backend_conn(connId)
    _write(backend_conn, data) # 将需要传输的数据发送给后端连接
  else: # mode == 'proxy'
    conn_id = connection(ctx, fctx) # 11.获取要发往哪个后端连接
    backend_conn = get_backend_conn(connId)
    if len > PROXY_ZERO_COPY_THRESHOLD():
      _proxy_zero_copy(from=frontend_conn, to=backend_conn, len) # 代理给后端
    else:
      _proxy(from=frontend_conn, to=backend_conn, len) # 代理给后端
    proxyDone(ctx, fctx) # 12.代理完成

# 获取后端连接
def get_backend_conn(conn_id):
  if (conn_id != -1): # 如果指定了connId
    return _existing_connection(conn_id) # 获取已有连接

  backend_conn = _get_backend_connection() # 获取一个后端连接
  conn_id = _get_next_conn_id()
  bctx = initSub(ctx, conn_id) # 8.创建后端连接的子上下文
  bdata = connected(ctx, bctx) # 9.获取连接建立时需要发送的数据
  chosen(ctx, fctx, bctx) # 10.告诉处理器后端连接已选中
  _write(backend_conn, bdata) # 发送建立连接时需要发送的数据

# 13.当后端连接有数据时
def on_backend_data():
  len = len(ctx, bctx) # 14.获取所需数据长度
  if (mode(ctx, bctx) == 'handle'):
    data = _read(conn, len) # 从后端连接获取长度为len的数据
    data = feed(ctx, bctx, data) # 15.获取需要发送给前端的数据
    data2 = produce(ctx, bctx) # 16.获取需要回给后端的数据
    _write(backend_conn, data2) # 回应给后端连接
    _write(frontend_conn, data) # 发送给前端连接
  else: # mode == 'proxy'
    if len > PROXY_ZERO_COPY_THRESHOLD():
      _proxy_zero_copy(from=backend_conn, to=frontend_conn, len)
    else:
      _proxy(from=backend_conn, to=frontend_conn, len)
    proxyDone(ctx, bctx) # 17.代理完成
  if (expectNewFrame(ctx, bctx)): #18.检查是否之前分片的传输已经完成
    _handle_another_backend()
```
