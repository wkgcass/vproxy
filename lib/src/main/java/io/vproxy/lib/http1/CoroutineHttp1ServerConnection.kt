package io.vproxy.lib.http1

import io.vproxy.lib.http.HttpServerConnection
import io.vproxy.lib.http.HttpServerResponse
import io.vproxy.lib.tcp.CoroutineConnection

class CoroutineHttp1ServerConnection(val conn: CoroutineConnection) : HttpServerConnection, AutoCloseable {
  override fun base(): io.vproxy.base.connection.Connection {
    return conn.conn
  }

  override fun response(status: Int): HttpServerResponse {
    return response(status, io.vproxy.base.util.web.HttpStatusCodeReasonMap.get(status))
  }

  fun response(status: Int, reason: String): CoroutineHttp1Response {
    return CoroutineHttp1Response(status, reason)
  }

  inner class CoroutineHttp1Response(private val status: Int, private val reason: String) :
    CoroutineHttp1Common(conn), HttpServerResponse {
    private val headers = ArrayList<io.vproxy.base.processor.http1.entity.Header>()

    override fun header(key: String, value: String): CoroutineHttp1Response {
      headers.add(io.vproxy.base.processor.http1.entity.Header(key, value))
      return this
    }

    @Suppress("DuplicatedCode")
    override suspend fun send(body: io.vproxy.base.util.ByteArray?) {
      val resp = io.vproxy.base.processor.http1.entity.Response()
      resp.version = "HTTP/1.1"
      resp.statusCode = status
      resp.reason = reason
      if (body != null && body.length() > 0) {
        headers.add(io.vproxy.base.processor.http1.entity.Header("content-length", "" + body.length()))
      } else {
        headers.add(io.vproxy.base.processor.http1.entity.Header("content-length", "0"))
      }
      resp.headers = headers
      resp.body = body

      headersSent = true
      conn.write(resp.toByteArray())
    }

    override suspend fun sendHeadersBeforeChunks() {
      if (headersSent) {
        return
      }

      val resp = io.vproxy.base.processor.http1.entity.Response()
      resp.version = "HTTP/1.1"
      resp.statusCode = status
      resp.reason = reason
      headers.add(io.vproxy.base.processor.http1.entity.Header("transfer-encoding", "chunked"))
      resp.headers = headers

      headersSent = true
      conn.write(resp.toByteArray())
    }

    override suspend fun sendChunk(payload: io.vproxy.base.util.ByteArray): CoroutineHttp1Response {
      super<CoroutineHttp1Common>.sendChunk(payload)
      return this
    }

    override suspend fun endChunks(trailers: List<io.vproxy.base.processor.http1.entity.Header>) {
      super.endChunks(trailers)
    }
  }

  /**
   * @return a full request object including body or chunks/trailers.
   * If eof received, the function returns null
   */
  suspend fun readRequest(): io.vproxy.base.processor.http1.entity.Request? {
    val parser = io.vproxy.base.http.HttpReqParser()
    return conn.read(parser)
  }

  override fun close() {
    conn.close()
  }
}
