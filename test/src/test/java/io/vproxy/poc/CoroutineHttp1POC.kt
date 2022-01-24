package io.vproxy.poc

import io.vproxy.lib.common.*

object CoroutineHttp1POC {
  @JvmStatic
  fun main(args: Array<String>) {
    val loop = io.vproxy.base.selector.SelectorEventLoop.open()
    loop.ensureNetEventLoop()
    loop.loop { io.vproxy.base.util.thread.VProxyThread.create(it, "coroutine-http1-poc") }
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
          val conn = serverSock.accept().asHttp1ServerConnection()
          println("accepted socket $conn")
          vplib.coroutine.with(conn).launch {
            while (true) {
              val req = conn.readRequest() ?: break
              println("server received request: $req")
              conn.response(200).header("Server", "vproxy/" + io.vproxy.base.util.Version.VERSION)
                .send("Hello World\r\n")
            }
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
      sock.setTimeout(1000)
      sock.connect()
      val conn = sock.asHttp1ClientConnection()
      conn.get("/").header("Host", "example.com").send()
      val resp = conn.readResponse()
      println("client received response: $resp")
      sock.close()

      unsafeIO { loop.close() }
    }
  }
}
