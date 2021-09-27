package io.vproxy.poc

import io.vproxy.base.connection.ConnectableConnection
import io.vproxy.base.connection.ConnectionOpts
import io.vproxy.base.connection.ServerSock
import io.vproxy.base.http.HttpReqParser
import io.vproxy.base.processor.http1.entity.Header
import io.vproxy.base.processor.http1.entity.Request
import io.vproxy.base.processor.http1.entity.Response
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.ByteArray
import io.vproxy.base.util.RingBuffer
import io.vproxy.base.util.Version
import io.vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.*
import io.vproxy.vfd.IPPort

object CoroutineTcpPOC {
  @JvmStatic
  fun main(args: Array<String>) {
    val loop = _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.open()
    loop.ensureNetEventLoop()
    loop.loop { _root_ide_package_.io.vproxy.base.util.thread.VProxyThread.create(it, "coroutine-tcp-poc") }
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
          val sock = serverSock.accept()
          println("accepted socket $sock")
          vplib.coroutine.with(sock).launch {
            val rb = sock.read()
            val parser = _root_ide_package_.io.vproxy.base.http.HttpReqParser(true)
            parser.feed(rb) // expect to be completed in this example
            val req = parser.result
            println("server received request: $req")

            val resp = _root_ide_package_.io.vproxy.base.processor.http1.entity.Response()
            resp.version = "HTTP/1.1"
            resp.statusCode = 200
            resp.reason = "OK"
            resp.headers = listOf(
              _root_ide_package_.io.vproxy.base.processor.http1.entity.Header(
                "Server",
                "vproxy/" + _root_ide_package_.io.vproxy.base.util.Version.VERSION
              )
            )
            resp.body = _root_ide_package_.io.vproxy.base.util.ByteArray.from("Hello World\r\n")
            sock.write(resp.toByteArray())
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
      defer { sock.close() }
      sock.setTimeout(1000)
      sock.connect()
      val req = _root_ide_package_.io.vproxy.base.processor.http1.entity.Request()
      req.method = "GET"
      req.uri = "/"
      req.version = "HTTP/1.1"
      req.headers = listOf(_root_ide_package_.io.vproxy.base.processor.http1.entity.Header("Host", "example.com"))
      sock.write(req.toByteArray())
      val buf = _root_ide_package_.io.vproxy.base.util.ByteArray.allocate(1024)
      val n = sock.read(buf)
      println("client received response: " + buf.sub(0, n))
    }
  }
}
