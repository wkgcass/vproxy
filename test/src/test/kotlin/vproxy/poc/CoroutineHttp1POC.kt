package vproxy.poc

import vproxy.base.connection.ConnectableConnection
import vproxy.base.connection.ConnectionOpts
import vproxy.base.connection.ServerSock
import vproxy.base.selector.SelectorEventLoop
import vproxy.base.util.RingBuffer
import vproxy.base.util.Version
import vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.*
import vproxy.vfd.IPPort

object CoroutineHttp1POC {
  @JvmStatic
  fun main(args: Array<String>) {
    val loop = SelectorEventLoop.open()
    loop.ensureNetEventLoop()
    loop.loop { VProxyThread.create(it, "coroutine-http1-poc") }
    loop.with(loop).launch {
      val listenPort = 30080
      vproxy.coroutine.launch {
        // start server
        val serverSock = unsafeIO { ServerSock.create(IPPort("127.0.0.1", listenPort)).coroutine() }
        defer { serverSock.close() }
        while (true) {
          val conn = serverSock.accept().asHttp1ServerConnection()
          println("accepted socket $conn")
          vproxy.coroutine.with(conn).launch {
            while (true) {
              val req = conn.readRequest() ?: break
              println("server received request: $req")
              conn.response(200).header("Server", "vproxy/" + Version.VERSION)
                .send("Hello World\r\n")
            }
          }
        }
      }

      println("wait for 1 sec on thread: " + Thread.currentThread())
      sleep(1000)
      println("begin request on thread: " + Thread.currentThread())

      val sock = unsafeIO {
        ConnectableConnection.create(
          IPPort("127.0.0.1", listenPort), ConnectionOpts(),
          RingBuffer.allocate(1024), RingBuffer.allocate(1024)
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
