package io.vproxy.lib.http1

import io.vproxy.dep.vjson.JSON
import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.execute
import io.vproxy.lib.common.unsafeIO
import io.vproxy.lib.tcp.CoroutineConnection
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLEngine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("LiftReturnOrAssignment")
class CoroutineHttp1ClientConnection(val conn: CoroutineConnection) : AutoCloseable {
  fun get(url: String): CoroutineHttp1Request {
    return request("GET", url)
  }

  fun post(url: String): CoroutineHttp1Request {
    return request("POST", url)
  }

  fun put(url: String): CoroutineHttp1Request {
    return request("PUT", url)
  }

  fun del(url: String): CoroutineHttp1Request {
    return request("DELETE", url)
  }

  fun request(method: String, url: String): CoroutineHttp1Request {
    return CoroutineHttp1Request(method, url)
  }

  inner class CoroutineHttp1Request(private val method: String, private val url: String) :
    CoroutineHttp1Common(conn) {
    private val headers = ArrayList<io.vproxy.base.processor.http1.entity.Header>()

    fun header(key: String, value: String): CoroutineHttp1Request {
      headers.add(io.vproxy.base.processor.http1.entity.Header(key, value))
      return this
    }

    suspend fun send() {
      send(null)
    }

    suspend fun send(body: String) {
      send(io.vproxy.base.util.ByteArray.from(body))
    }

    suspend fun send(json: JSON.Instance<*>) {
      send(json.stringify())
    }

    @Suppress("DuplicatedCode")
    suspend fun send(body: io.vproxy.base.util.ByteArray?) {
      val req = io.vproxy.base.processor.http1.entity.Request()
      req.method = method
      req.uri = url
      req.version = "HTTP/1.1"
      if (body != null && body.length() > 0) {
        headers.add(io.vproxy.base.processor.http1.entity.Header("content-length", "" + body.length()))
      } else {
        headers.add(io.vproxy.base.processor.http1.entity.Header("content-length", "0"))
      }
      req.headers = headers
      req.body = body
      conn.write(req.toByteArray())
    }

    override suspend fun sendHeadersBeforeChunks() {
      val req = io.vproxy.base.processor.http1.entity.Request()
      req.method = method
      req.uri = url
      req.version = "HTTP/1.1"
      headers.add(io.vproxy.base.processor.http1.entity.Header("transfer-encoding", "chunked"))
      req.headers = headers
      conn.write(req.toByteArray())
    }
  }

  /**
   * @return a full response object including body or chunks/trailers.
   * If eof received, an IOException would be thrown instead of returning null Response
   */
  suspend fun readResponse(): io.vproxy.base.processor.http1.entity.Response {
    val parser = io.vproxy.base.http.HttpRespParser(true)
    while (true) {
      val rb = conn.read() ?: throw IOException("unexpected eof")
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

  @Suppress("HttpUrlsUsage", "CascadeIf")
  companion object {
    @JvmStatic
    fun simpleGet(full: String): io.vproxy.base.util.promise.Promise<io.vproxy.base.util.ByteArray> {
      val invokerLoop = io.vproxy.base.selector.SelectorEventLoop.current()

      val protocolAndHostAndPort: String
      val uri: String

      val protocol: String
      val hostAndPortAndUri: String
      if (full.startsWith("http://")) {
        protocol = "http://"
        hostAndPortAndUri = full.substring("http://".length)
      } else if (full.startsWith("https://")) {
        protocol = "https://"
        hostAndPortAndUri = full.substring("https://".length)
      } else {
        throw Exception("unknown protocol in $full")
      }
      if (hostAndPortAndUri.contains("/")) {
        protocolAndHostAndPort = protocol + hostAndPortAndUri.substring(0, hostAndPortAndUri.indexOf("/"))
        uri = hostAndPortAndUri.substring(hostAndPortAndUri.indexOf("/"))
      } else {
        protocolAndHostAndPort = hostAndPortAndUri
        uri = "/"
      }

      val loop: io.vproxy.base.selector.SelectorEventLoop
      if (invokerLoop == null) {
        loop = io.vproxy.base.selector.SelectorEventLoop.open()
        loop.ensureNetEventLoop()
        loop.loop { io.vproxy.base.util.thread.VProxyThread.create(it, "http1-simple-get") }
      } else {
        loop = invokerLoop
      }

      return loop.execute {
        val conn = create(protocolAndHostAndPort)
        defer { conn.close() }
        if (invokerLoop == null) { // which means a new loop is created
          defer { loop.close() }
        }

        conn.conn.setTimeout(5_000)
        val req = conn.get(uri)
        val host = getHostFromUrl(protocolAndHostAndPort)
        if (host != null) {
          req.header("Host", host)
        }
        req.send()
        val resp = conn.readResponse()
        val ret: io.vproxy.base.util.ByteArray
        if (resp.statusCode != 200) {
          throw IOException("request failed: response status is " + resp.statusCode + " instead of 200")
        } else {
          ret = resp.getBody()
        }
        ret
      }
    }

    private fun getHostFromUrl(url: String): String? {
      var x = url
      if (x.contains("://")) {
        x = x.substring(x.indexOf("://") + "://".length)
      }
      if (x.contains("/")) {
        x = x.substring(0, x.indexOf("/"))
      }
      if (io.vproxy.vfd.IP.isIpLiteral(x)) {
        return null
      }
      if (x.contains(":")) {
        x = x.substring(0, x.lastIndexOf(":"))
      }
      if (io.vproxy.vfd.IP.isIpLiteral(x)) {
        return null
      }
      return x
    }

    suspend fun create(protocolAndHostAndPort: String): CoroutineHttp1ClientConnection {
      val ssl: Boolean
      val hostAndPort: String
      val host: String
      val port: Int

      if (protocolAndHostAndPort.startsWith("http://")) {
        ssl = false
        hostAndPort = protocolAndHostAndPort.substring("http://".length)
      } else if (protocolAndHostAndPort.startsWith("https://")) {
        ssl = true
        hostAndPort = protocolAndHostAndPort.substring("https://".length)
      } else {
        ssl = false
        hostAndPort = protocolAndHostAndPort
      }
      if (io.vproxy.vfd.IP.isIpLiteral(hostAndPort)) {
        host = hostAndPort
        port = if (ssl) {
          443
        } else {
          80
        }
      } else if (hostAndPort.contains(":")) {
        host = hostAndPort.substring(0, hostAndPort.lastIndexOf(":"))
        val portStr = hostAndPort.substring(hostAndPort.lastIndexOf(":") + 1)
        try {
          port = Integer.parseInt(portStr)
        } catch (e: NumberFormatException) {
          throw IllegalArgumentException("invalid port number")
        }
      } else {
        host = hostAndPort
        port = if (ssl) {
          443
        } else {
          80
        }
      }

      // resolve
      val ip = if (io.vproxy.vfd.IP.isIpLiteral(host)) {
        io.vproxy.vfd.IP.from(host)
      } else {
        suspendCancellableCoroutine { cont ->
          io.vproxy.base.dns.Resolver.getDefault().resolve(host, object : io.vproxy.base.util.callback.Callback<io.vproxy.vfd.IP, UnknownHostException>() {
            override fun onSucceeded(value: io.vproxy.vfd.IP) {
              cont.resume(value)
            }

            override fun onFailed(err: UnknownHostException) {
              cont.resumeWithException(err)
            }
          })
        }
      }

      // now try to connect
      if (ssl) {
        val sslContext = io.vproxy.base.util.ringbuffer.SSLUtils.getDefaultClientSSLContext()
        val engine: SSLEngine
        if (io.vproxy.vfd.IP.isIpLiteral(host)) {
          engine = sslContext.createSSLEngine()
        } else {
          engine = sslContext.createSSLEngine(host, port)
        }
        return create(io.vproxy.vfd.IPPort(ip, port), engine)
      } else {
        return create(io.vproxy.vfd.IPPort(ip, port))
      }
    }

    suspend fun create(ipport: io.vproxy.vfd.IPPort, engine: SSLEngine): CoroutineHttp1ClientConnection {
      engine.useClientMode = true
      val pair = io.vproxy.base.util.ringbuffer.SSLUtils.genbuf(
        engine, io.vproxy.base.util.RingBuffer.allocate(24576), io.vproxy.base.util.RingBuffer.allocate(24576),
        io.vproxy.base.selector.SelectorEventLoop.current(), ipport
      )
      val conn = unsafeIO {
        io.vproxy.base.connection.ConnectableConnection.create(
          ipport, io.vproxy.base.connection.ConnectionOpts(),
          pair.left, pair.right
        ).coroutine()
      }
      conn.connect()
      return conn.asHttp1ClientConnection()
    }

    suspend fun create(ipport: io.vproxy.vfd.IPPort): CoroutineHttp1ClientConnection {
      val conn = unsafeIO {
        io.vproxy.base.connection.ConnectableConnection.create(
          ipport, io.vproxy.base.connection.ConnectionOpts(),
          io.vproxy.base.util.RingBuffer.allocate(16384), io.vproxy.base.util.RingBuffer.allocate(16384)
        ).coroutine()
      }
      conn.connect()
      return conn.asHttp1ClientConnection()
    }
  }
}
