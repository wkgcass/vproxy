package vproxy.poc

import vproxy.base.connection.ConnectableConnection
import vproxy.base.connection.ConnectionOpts
import vproxy.base.connection.ServerSock
import vproxy.base.http.HttpReqParser
import vproxy.base.processor.http1.entity.Header
import vproxy.base.processor.http1.entity.Request
import vproxy.base.processor.http1.entity.Response
import vproxy.base.selector.SelectorEventLoop
import vproxy.base.util.ByteArray
import vproxy.base.util.RingBuffer
import vproxy.base.util.Version
import vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.*
import vproxy.vfd.IPPort

object CoroutineTcpPOC {
  @JvmStatic
  fun main(args: Array<String>) {
    val loop = SelectorEventLoop.open()
    loop.ensureNetEventLoop()
    loop.loop { VProxyThread.create(it, "coroutine-tcp-poc") }
    loop.with(loop).launch {
      val listenPort = 30080
      vplib.coroutine.launch {
        // start server
        val serverSock = unsafeIO { ServerSock.create(IPPort("127.0.0.1", listenPort)).coroutine() }
        defer { serverSock.close() }
        while (true) {
          val sock = serverSock.accept()
          println("accepted socket $sock")
          vplib.coroutine.with(sock).launch {
            val rb = sock.read()
            val parser = HttpReqParser(true)
            parser.feed(rb) // expect to be completed in this example
            val req = parser.result
            println("server received request: $req")

            val resp = Response()
            resp.version = "HTTP/1.1"
            resp.statusCode = 200
            resp.reason = "OK"
            resp.headers = listOf(Header("Server", "vproxy/" + Version.VERSION))
            resp.body = ByteArray.from("Hello World\r\n")
            sock.write(resp.toByteArray())
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
      defer { sock.close() }
      sock.setTimeout(1000)
      sock.connect()
      val req = Request()
      req.method = "GET"
      req.uri = "/"
      req.version = "HTTP/1.1"
      req.headers = listOf(Header("Host", "example.com"))
      sock.write(req.toByteArray())
      val buf = ByteArray.allocate(1024)
      val n = sock.read(buf)
      println("client received response: " + buf.sub(0, n))
    }
  }
}
