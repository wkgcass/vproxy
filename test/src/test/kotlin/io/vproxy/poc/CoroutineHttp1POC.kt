package io.vproxy.poc

import io.vproxy.base.connection.ConnectableConnection
import io.vproxy.base.connection.ConnectionOpts
import io.vproxy.base.connection.ServerSock
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.RingBuffer
import io.vproxy.base.util.Version
import io.vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.*
import io.vproxy.vfd.IPPort

object CoroutineHttp1POC {
  @JvmStatic
  fun main(args: Array<String>) {
    val loop = _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.open()
    loop.ensureNetEventLoop()
    loop.loop { _root_ide_package_.io.vproxy.base.util.thread.VProxyThread.create(it, "coroutine-http1-poc") }
    loop.with(loop).launch {
      val listenPort = 30080
      vplib.coroutine.launch {
        // start server
        val serverSock = unsafeIO { _root_ide_package_.io.vproxy.base.connection.ServerSock.create(
          _root_ide_package_.io.vproxy.vfd.IPPort(
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
              conn.response(200).header("Server", "vproxy/" + _root_ide_package_.io.vproxy.base.util.Version.VERSION)
                .send("Hello World\r\n")
            }
          }
        }
      }

      println("wait for 1 sec on thread: " + Thread.currentThread())
      sleep(1000)
      println("begin request on thread: " + Thread.currentThread())

      val sock = unsafeIO {
        _root_ide_package_.io.vproxy.base.connection.ConnectableConnection.create(
          _root_ide_package_.io.vproxy.vfd.IPPort("127.0.0.1", listenPort), _root_ide_package_.io.vproxy.base.connection.ConnectionOpts(),
          _root_ide_package_.io.vproxy.base.util.RingBuffer.allocate(1024), _root_ide_package_.io.vproxy.base.util.RingBuffer.allocate(1024)
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
