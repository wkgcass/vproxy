package io.vproxy.lib.http1

import io.vproxy.base.connection.ConnectableConnection
import io.vproxy.base.connection.ConnectionOpts
import io.vproxy.base.dns.Resolver
import io.vproxy.base.http.HttpRespParser
import io.vproxy.base.processor.http1.entity.Header
import io.vproxy.base.processor.http1.entity.Request
import io.vproxy.base.processor.http1.entity.Response
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.ByteArray
import io.vproxy.base.util.RingBuffer
import io.vproxy.base.util.callback.Callback
import io.vproxy.base.util.promise.Promise
import io.vproxy.base.util.ringbuffer.SSLUtils
import io.vproxy.base.util.thread.VProxyThread
import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.execute
import io.vproxy.lib.common.unsafeIO
import io.vproxy.lib.tcp.CoroutineConnection
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPPort
import kotlinx.coroutines.suspendCancellableCoroutine
import vjson.JSON
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

    suspend fun send(json: JSON.Instance<*>) {
      send(json.stringify())
    }

    @Suppress("DuplicatedCode")
    suspend fun send(body: ByteArray?) {
      val req = Request()
      req.method = method
      req.uri = url
      req.version = "HTTP/1.1"
      if (body != null && body.length() > 0) {
        headers.add(Header("content-length", "" + body.length()))
      } else {
        headers.add(Header("content-length", "0"))
      }
      req.headers = headers
      req.body = body

      headersSent = true
      conn.write(req.toByteArray())
    }

    override suspend fun sendHeadersBeforeChunks() {
      if (headersSent) {
        return
      }

      val req = Request()
      req.method = method
      req.uri = url
      req.version = "HTTP/1.1"
      headers.add(Header("transfer-encoding", "chunked"))
      req.headers = headers

      headersSent = true
      conn.write(req.toByteArray())
    }
  }

  /**
   * @return a full response object including body or chunks/trailers.
   * If eof received, an IOException would be thrown instead of returning null Response
   */
  suspend fun readResponse(): Response {
    val parser = HttpRespParser(true)
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

  companion object {
    @JvmStatic
    fun simpleGet(full: String): Promise<ByteArray> {
      return simpleGet(full, false)
    }

    @JvmStatic
    fun simpleGet(full: String, loop: SelectorEventLoop?, opts: ConnectionOpts): Promise<ByteArray> {
      return simpleGet(full, false, loop, opts)
    }

    @JvmStatic
    fun simpleGet(full: String, mustRunOnNewThread: Boolean): Promise<ByteArray> {
      return simpleGet(full, mustRunOnNewThread, null, ConnectionOpts())
    }

    @Suppress("HttpUrlsUsage")
    @JvmStatic
    fun simpleGet(full: String, mustRunOnNewThread: Boolean, loop: SelectorEventLoop?, opts: ConnectionOpts): Promise<ByteArray> {
      val invokerLoop = loop ?: SelectorEventLoop.current()

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

      val execLoop = if (invokerLoop == null || mustRunOnNewThread) {
        val el = SelectorEventLoop.open()
        el.ensureNetEventLoop()
        el.loop { VProxyThread.create(it, "http1-simple-get") }
        el
      } else {
        invokerLoop
      }

      return execLoop.execute {
        if (invokerLoop != execLoop) { // which means a new loop is created
          defer { execLoop.close() }
        }
        val conn = create(protocolAndHostAndPort, opts)
        defer { conn.close() }

        conn.conn.setTimeout(5_000)
        val req = conn.get(uri)
        val host = getHostFromUrl(protocolAndHostAndPort)
        req.header("Host", host)
        req.send()
        val resp = conn.readResponse()
        val ret: ByteArray
        if (resp.statusCode != 200) {
          throw IOException("request failed: response status is " + resp.statusCode + " instead of 200")
        } else {
          ret = resp.getBody()
        }
        ret
      }
    }

    private fun getHostFromUrl(url: String): String {
      var x = url
      if (x.contains("://")) {
        x = x.substring(x.indexOf("://") + "://".length)
      }
      if (x.contains("/")) {
        x = x.substring(0, x.indexOf("/"))
      }
      if (IP.isIpLiteral(x)) {
        return x
      }
      if (x.contains(":")) {
        x = x.substring(0, x.lastIndexOf(":"))
      }
      if (IP.isIpLiteral(x)) {
        return x
      }
      return x
    }

    @Suppress("HttpUrlsUsage")
    suspend fun create(protocolAndHostAndPort: String, opts: ConnectionOpts = ConnectionOpts.getDefault()): CoroutineHttp1ClientConnection {
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
      if (IP.isIpLiteral(hostAndPort)) {
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
      val ip = if (IP.isIpLiteral(host)) {
        IP.from(host)
      } else {
        suspendCancellableCoroutine { cont ->
          Resolver.getDefault().resolve(host, object : Callback<IP, UnknownHostException>() {
            override fun onSucceeded(value: IP) {
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
        val sslContext = SSLUtils.getDefaultClientSSLContext()
        val engine: SSLEngine
        if (IP.isIpLiteral(host)) {
          engine = sslContext.createSSLEngine()
        } else {
          engine = sslContext.createSSLEngine(host, port)
        }
        return create(IPPort(ip, port), engine, opts)
      } else {
        return create(IPPort(ip, port), opts)
      }
    }

    suspend fun create(
      ipport: IPPort,
      engine: SSLEngine,
      opts: ConnectionOpts = ConnectionOpts.getDefault()
    ): CoroutineHttp1ClientConnection {
      engine.useClientMode = true
      val pair = SSLUtils.genbuf(
        engine, RingBuffer.allocate(24576), RingBuffer.allocate(24576),
        SelectorEventLoop.current(), ipport
      )
      val conn = unsafeIO {
        ConnectableConnection.create(
          ipport, opts,
          pair.left, pair.right
        ).coroutine()
      }
      conn.connect()
      return conn.asHttp1ClientConnection()
    }

    suspend fun create(ipport: IPPort, opts: ConnectionOpts = ConnectionOpts.getDefault()): CoroutineHttp1ClientConnection {
      val conn = unsafeIO {
        ConnectableConnection.create(
          ipport, opts,
          RingBuffer.allocate(16384), RingBuffer.allocate(16384)
        ).coroutine()
      }
      conn.connect()
      return conn.asHttp1ClientConnection()
    }
  }
}
