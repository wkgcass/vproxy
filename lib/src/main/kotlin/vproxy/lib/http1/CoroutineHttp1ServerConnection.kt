package vproxy.lib.http1

import vproxy.base.http.HttpReqParser
import vproxy.base.processor.http1.entity.Header
import vproxy.base.processor.http1.entity.Request
import vproxy.base.processor.http1.entity.Response
import vproxy.base.util.ByteArray
import vproxy.base.util.HttpStatusCodeReasonMap
import vproxy.lib.tcp.CoroutineConnection
import java.io.IOException

class CoroutineHttp1ServerConnection(val conn: CoroutineConnection) {
  fun newResponse(status: Int): CoroutineHttp1Response {
    return newResponse(status, HttpStatusCodeReasonMap.get(status))
  }

  fun newResponse(status: Int, reason: String): CoroutineHttp1Response {
    return CoroutineHttp1Response(status, reason)
  }

  inner class CoroutineHttp1Response(private val status: Int, private val reason: String) :
    CoroutineHttp1Common(conn) {
    private val headers = ArrayList<Header>()

    fun header(key: String, value: String): CoroutineHttp1Response {
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
      val resp = Response()
      resp.version = "HTTP/1.1"
      resp.statusCode = status
      resp.reason = reason
      if (body != null && body.length() > 0) {
        headers.add(Header("content-length", "" + body.length()))
      }
      resp.headers = headers
      resp.body = body
      conn.write(resp.toByteArray())
    }

    override suspend fun sendHeadersBeforeChunks() {
      val resp = Response()
      resp.version = "HTTP/1.1"
      resp.statusCode = status
      resp.reason = reason
      headers.add(Header("transfer-encoding", "chunked"))
      resp.headers = headers
      conn.write(resp.toByteArray())
    }
  }

  @Suppress("DuplicatedCode")
  suspend fun readRequest(): Request {
    val parser = HttpReqParser(true)
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
