package vproxy.test.cases

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import vproxy.base.connection.ServerSock
import vproxy.base.processor.http1.entity.Response
import vproxy.base.selector.SelectorEventLoop
import vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.coroutine
import vproxy.lib.common.execute
import vproxy.lib.common.launch
import vproxy.lib.http.RoutingHandlerFunc
import vproxy.lib.http1.CoroutineHttp1ClientConnection
import vproxy.lib.http1.CoroutineHttp1Server
import vproxy.vfd.IPPort
import java.io.IOException

class TestHttpServer {
  companion object {
    private const val port = 30080
    private var server: CoroutineHttp1Server? = null
    private var loop: SelectorEventLoop? = null

    @JvmStatic
    @BeforeClass
    @Throws(Exception::class)
    fun beforeClass() {
      val serverSock = ServerSock.create(IPPort("127.0.0.1", port))
      val loop = SelectorEventLoop.open()
      this.loop = loop
      loop.ensureNetEventLoop()
      loop.loop { VProxyThread.create(it, "test-http-server") }
      val server = CoroutineHttp1Server(serverSock.coroutine(loop.ensureNetEventLoop()))
      this.server = server

      loop.launch {
        val simpleHandler: RoutingHandlerFunc = { it.conn.response(200).send(it.req.method() + " " + it.req.uri()) }

        // simple response
        server.get("/simple-response", simpleHandler)

        // path param
        server.get("/path-1/:path-param") { it.conn.response(200).send(it.param("path-param")) }

        // simple filter and response
        server.get("/simple-filter-response") { it.putParam("foo", "bar-s").allowNext() }
        server.get("/simple-filter-response") { it.conn.response(200).send(it.param("foo") + " - " + it.req.uri()) }

        // path param filter
        server.get("/path-2/:path-param-1") { it.putParam("pass", it.param("path-param-1")).allowNext() }
        server.get("/path-2/:path-param-2") { it.conn.response(200).send(it.param("pass") + " - " + it.param("path-param-2")) }

        // wildcard
        server.get("/path-3/*", simpleHandler)

        // wildcard filter
        server.get("/path-4/*") { it.putParam("foo", "bar-4").allowNext() }
        server.get("/path-4/*") { it.conn.response(200).send(it.param("foo") + " - " + it.req.uri()) }

        // param and wildcard
        server.get("/path-5/:param/middle/*") { it.conn.response(200).send(it.param("param") + " - " + it.req.uri()) }

        // param and wildcard filter
        server.get("/path-6/:param/middle/*") { rctx -> rctx.putParam("pass", rctx.param("param")).allowNext() }
        server.get("/path-6/:param2/middle/*") {
          it.conn.response(200).send(
            it.param("param") + " - " + it.param("pass") + " - " + it.req.uri()
          )
        }

        // start
        server.start()
      }
    }

    @JvmStatic
    @AfterClass
    fun afterClass() {
      server!!.close()
      loop!!.close()
    }
  }

  private fun requestRaw(uri: String): Response {
    val response = loop!!.execute {
      val client = CoroutineHttp1ClientConnection.create(IPPort("127.0.0.1", port))
      client.get(uri).send()
      val resp = client.readResponse()
      client.conn.close()
      resp
    }.block()
    return response
  }

  private fun request(uri: String): String {
    val body = loop!!.execute {
      val client = CoroutineHttp1ClientConnection.create(IPPort("127.0.0.1", port))
      defer { client.close() }

      client.get(uri).send()
      val resp = client.readResponse()
      if (resp.statusCode != 200) {
        throw IOException("response status code not 200: " + resp.statusCode)
      }
      val body = resp.body
      if (body != null) {
        String(body.toJavaArray())
      } else {
        ""
      }
    }.block()
    return body
  }

  @Test
  @Throws(Exception::class)
  fun simple() {
    var res: String
    res = request("/simple-response")
    assertEquals("GET /simple-response", res)
    res = request("/path-1/hh")
    assertEquals("hh", res)
    res = request("/path-1/gg")
    assertEquals("gg", res)
    res = request("/simple-filter-response")
    assertEquals("bar-s - /simple-filter-response", res)
    res = request("/path-2/hh")
    assertEquals("hh - hh", res)
    res = request("/path-2/gg")
    assertEquals("gg - gg", res)
    res = request("/path-3/hh")
    assertEquals("GET /path-3/hh", res)
    res = request("/path-3/gg")
    assertEquals("GET /path-3/gg", res)
    res = request("/path-3/hh/gg")
    assertEquals("GET /path-3/hh/gg", res)
    res = request("/path-4/hh")
    assertEquals("bar-4 - /path-4/hh", res)
    res = request("/path-4/gg")
    assertEquals("bar-4 - /path-4/gg", res)
    res = request("/path-4/hh/gg")
    assertEquals("bar-4 - /path-4/hh/gg", res)
    res = request("/path-5/hh/middle/gg")
    assertEquals("hh - /path-5/hh/middle/gg", res)
    res = request("/path-5/hhgg/middle/gghh")
    assertEquals("hhgg - /path-5/hhgg/middle/gghh", res)
  }

  @Test
  @Throws(Exception::class)
  fun test404() {
    var resp = requestRaw("/")
    assertEquals(404, resp.statusCode)
    assertEquals("Cannot GET /\r\n", resp.body.toString())
    resp = requestRaw("/abc")
    assertEquals(404, resp.statusCode)
    assertEquals("Cannot GET /abc\r\n", resp.body.toString())
  }
}
