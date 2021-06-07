package vproxy.lib.http1

import vproxy.base.http.HttpRespParser
import vproxy.base.processor.http1.entity.Header
import vproxy.base.processor.http1.entity.Request
import vproxy.base.processor.http1.entity.Response
import vproxy.base.util.ByteArray
import vproxy.lib.tcp.CoroutineConnection
import java.io.IOException

class CoroutineHttp1ClientConnection(val conn: CoroutineConnection) {
  fun get(url: String): CoroutineHttp1Request {
    return newRequest("GET", url)
  }

  fun post(url: String): CoroutineHttp1Request {
    return newRequest("POST", url)
  }

  fun put(url: String): CoroutineHttp1Request {
    return newRequest("PUT", url)
  }

  fun del(url: String): CoroutineHttp1Request {
    return newRequest("DELETE", url)
  }

  fun newRequest(method: String, url: String): CoroutineHttp1Request {
    return CoroutineHttp1Request(method, url)
  }

  inner class CoroutineHttp1Request(private val method: String, private val url: String) :
    CoroutineHttp1Common(conn) {
    private val headers = ArrayList<Header>()

    fun header(key: String, value: String): CoroutineHttp1Request {
      headers.add(Header(key, value))
      return this
    }

    suspend fun send() {
      send(null)
    }

    suspend fun send(body: String) {
      send(ByteArray.from(body))
    }

    @Suppress("DuplicatedCode")
    suspend fun send(body: ByteArray?) {
      val req = Request()
      req.method = method
      req.uri = url
      req.version = "HTTP/1.1"
      if (body != null && body.length() > 0) {
        headers.add(Header("content-length", "" + body.length()))
      }
      req.headers = headers
      req.body = body
      conn.write(req.toByteArray())
    }

    override suspend fun sendHeadersBeforeChunks() {
      val req = Request()
      req.method = method
      req.uri = url
      req.version = "HTTP/1.1"
      headers.add(Header("transfer-encoding", "chunked"))
      req.headers = headers
      conn.write(req.toByteArray())
    }
  }

  @Suppress("DuplicatedCode")
  suspend fun readResponse(): Response {
    val parser = HttpRespParser(true)
    while (true) {
      val res = parser.feed(conn.read())
      if (res == -1) {
        if (parser.errorMessage != null) {
          throw IOException(parser.errorMessage)
        }
        continue
      }
      // done
      return parser.result
    }
  }
}
