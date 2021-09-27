package io.vproxy.poc

import io.vproxy.lib.common.*

object CoroutineTcpPOC {
  @JvmStatic
  fun main(args: Array<String>) {
    val loop = io.vproxy.base.selector.SelectorEventLoop.open()
    loop.ensureNetEventLoop()
    loop.loop { io.vproxy.base.util.thread.VProxyThread.create(it, "coroutine-tcp-poc") }
    loop.with(loop).launch {
      val listenPort = 30080
      vplib.coroutine.launch {
        // start server
        val serverSock = unsafeIO { io.vproxy.base.connection.ServerSock.create(
          io.vproxy.vfd.IPPort(
            "127.0.0.1",
            listenPort
          )
        ).coroutine() }
        defer { serverSock.close() }
        while (true) {
          val sock = serverSock.accept()
          println("accepted socket $sock")
          vplib.coroutine.with(sock).launch {
            val rb = sock.read()
            val parser = io.vproxy.base.http.HttpReqParser(true)
            parser.feed(rb) // expect to be completed in this example
            val req = parser.result
            println("server received request: $req")

            val resp = io.vproxy.base.processor.http1.entity.Response()
            resp.version = "HTTP/1.1"
            resp.statusCode = 200
            resp.reason = "OK"
            resp.headers = listOf(
              io.vproxy.base.processor.http1.entity.Header(
                "Server",
                "vproxy/" + io.vproxy.base.util.Version.VERSION
              )
            )
            resp.body = io.vproxy.base.util.ByteArray.from("Hello World\r\n")
            sock.write(resp.toByteArray())
          }
        }
      }

      println("wait for 1 sec on thread: " + Thread.currentThread())
      sleep(1000)
      println("begin request on thread: " + Thread.currentThread())

      val sock = unsafeIO {
        io.vproxy.base.connection.ConnectableConnection.create(
          io.vproxy.vfd.IPPort("127.0.0.1", listenPort), io.vproxy.base.connection.ConnectionOpts(),
          io.vproxy.base.util.RingBuffer.allocate(1024), io.vproxy.base.util.RingBuffer.allocate(1024)
        ).coroutine()
      }
      defer { sock.close() }
      sock.setTimeout(1000)
      sock.connect()
      val req = io.vproxy.base.processor.http1.entity.Request()
      req.method = "GET"
      req.uri = "/"
      req.version = "HTTP/1.1"
      req.headers = listOf(io.vproxy.base.processor.http1.entity.Header("Host", "example.com"))
      sock.write(req.toByteArray())
      val buf = io.vproxy.base.util.ByteArray.allocate(1024)
      val n = sock.read(buf)
      println("client received response: " + buf.sub(0, n))
    }
  }
}
