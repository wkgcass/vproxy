package vproxy.lib.http1

import vproxy.base.connection.Connection
import vproxy.base.http.HttpReqParser
import vproxy.base.processor.http1.entity.Header
import vproxy.base.processor.http1.entity.Request
import vproxy.base.processor.http1.entity.Response
import vproxy.base.util.ByteArray
import vproxy.base.util.web.HttpStatusCodeReasonMap
import vproxy.lib.http.HttpServerConnection
import vproxy.lib.http.HttpServerResponse
import vproxy.lib.tcp.CoroutineConnection
import java.io.IOException

class CoroutineHttp1ServerConnection(val conn: CoroutineConnection) : HttpServerConnection, AutoCloseable {
  override fun base(): Connection {
    return conn.conn
  }

  override fun response(status: Int): HttpServerResponse {
    return response(status, HttpStatusCodeReasonMap.get(status))
  }

  fun response(status: Int, reason: String): CoroutineHttp1Response {
    return CoroutineHttp1Response(status, reason)
  }

  inner class CoroutineHttp1Response(private val status: Int, private val reason: String) :
    CoroutineHttp1Common(conn), HttpServerResponse {
    private val headers = ArrayList<Header>()

    override fun header(key: String, value: String): CoroutineHttp1Response {
      headers.add(Header(key, value))
      return this
    }

    @Suppress("DuplicatedCode")
    override suspend fun send(body: ByteArray?) {
      val resp = Response()
      resp.version = "HTTP/1.1"
      resp.statusCode = status
      resp.reason = reason
      if (body != null && body.length() > 0) {
        headers.add(Header("content-length", "" + body.length()))
      } else {
        headers.add(Header("content-length", "0"))
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

    override suspend fun sendChunk(payload: ByteArray): CoroutineHttp1Response {
      super<CoroutineHttp1Common>.sendChunk(payload)
      return this
    }

    override suspend fun endChunks(trailers: List<Header>) {
      super.endChunks(trailers)
    }
  }

  /**
   * @return a full request object including body or chunks/trailers.
   * If eof received, the function returns null
   */
  suspend fun readRequest(): Request? {
    val parser = HttpReqParser(true)
    var started = false
    while (true) {
      val rb = conn.read() ?: if (started) {
        throw IOException("unexpected eof")
      } else {
        return null
      }
      started = true

      val res = parser.feed(rb)
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

  override fun close() {
    conn.close()
  }
}
