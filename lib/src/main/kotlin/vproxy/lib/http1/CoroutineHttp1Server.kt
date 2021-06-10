package vproxy.lib.http1

import vproxy.base.processor.http1.entity.Request
import vproxy.base.util.ByteArray
import vproxy.base.util.LogType
import vproxy.base.util.Logger
import vproxy.lib.common.vproxy
import vproxy.lib.http.GeneralCoroutineHttpServer
import vproxy.lib.http.HttpHeaders
import vproxy.lib.http.HttpServerRequest
import vproxy.lib.http.RoutingContext
import vproxy.lib.tcp.CoroutineConnection
import vproxy.lib.tcp.CoroutineServerSock

class CoroutineHttp1Server(val server: CoroutineServerSock) : GeneralCoroutineHttpServer<CoroutineHttp1Server>(), AutoCloseable {
  suspend fun start() {
    if (started) {
      throw IllegalStateException("This http1 server is already started")
    }
    started = true

    while (true) {
      val conn = server.accept()
      vproxy.coroutine.with(conn).launch {
        try {
          handleConnection(conn)
        } catch (e: Throwable) {
          Logger.error(LogType.CONN_ERROR, "failed handling connection as http1: $conn", e)
        }
      }
    }
  }

  override fun close() {
    server.close()
  }

  private var connectionHandler: (suspend (CoroutineConnection) -> Unit)? = null
  fun setConnectionHandler(handler: suspend (CoroutineConnection) -> Unit) {
    this.connectionHandler = handler
  }

  private suspend fun handleConnection(conn: CoroutineConnection) {
    val handler = this.connectionHandler
    if (handler != null) {
      try {
        handler(conn)
      } catch (e: Throwable) {
        Logger.error(LogType.IMPROPER_USE, "connectionHandler thrown exception when handling $conn", e)
        return
      }
    }

    val httpconn = conn.asHttp1ServerConnection()
    while (true) {
      val req = httpconn.readRequest() ?: break
      val ctx = RoutingContext(httpconn, ReqWrapper(req), routes)
      ctx.execute()
    }
  }

  private class ReqWrapper(val req: Request) : HttpServerRequest {
    private val headers = HeadersWrap(req)

    override fun method(): String {
      return req.method
    }

    override fun uri(): String {
      return req.uri
    }

    private var bodyCache: ByteArray? = null
    override fun headers(): HttpHeaders {
      return headers
    }

    override fun body(): ByteArray {
      if (bodyCache != null) {
        return bodyCache!!
      }
      if (req.body == null) {
        if (req.chunks == null) {
          return ByteArray.allocate(0)
        }
        // use chunks
        var ret: ByteArray? = null
        for (chunk in req.chunks) {
          if (ret == null) {
            ret = chunk.content
          } else {
            ret = ret.concat(chunk.content)
          }
        }
        bodyCache = ret
      } else {
        // use body
        bodyCache = req.body
      }
      if (bodyCache == null) {
        return ByteArray.allocate(0)
      }
      return bodyCache!!
    }
  }

  private class HeadersWrap(val req: Request) : HttpHeaders {
    private val cache = HashMap<String, String>()
    private var travelIndex = 0
    override fun get(name: String): String? {
      val nameLower = name.toLowerCase()
      if (cache.containsKey(nameLower)) {
        return cache.get(nameLower)
      }
      if (req.headers == null) {
        return null
      }
      while (travelIndex < req.headers.size) {
        val header = req.headers[travelIndex]
        ++travelIndex
        val key = header.key.toLowerCase()
        cache[key] = header.value
        if (key == nameLower) {
          return header.value
        }
      }
      return null
    }
  }
}
