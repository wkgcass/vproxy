package io.vproxy.lib.http1

import kotlinx.coroutines.suspendCancellableCoroutine
import vjson.JSON
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
import vproxy.lib.common.coroutine
import vproxy.lib.common.execute
import vproxy.lib.common.unsafeIO
import vproxy.lib.tcp.CoroutineConnection
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPPort
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
    private val headers = ArrayList<_root_ide_package_.io.vproxy.base.processor.http1.entity.Header>()

    fun header(key: String, value: String): CoroutineHttp1Request {
      headers.add(_root_ide_package_.io.vproxy.base.processor.http1.entity.Header(key, value))
      return this
    }

    suspend fun send() {
      send(null)
    }

    suspend fun send(body: String) {
      send(_root_ide_package_.io.vproxy.base.util.ByteArray.from(body))
    }

    suspend fun send(json: JSON.Instance<*>) {
      send(json.stringify())
    }

    @Suppress("DuplicatedCode")
    suspend fun send(body: _root_ide_package_.io.vproxy.base.util.ByteArray?) {
      val req = _root_ide_package_.io.vproxy.base.processor.http1.entity.Request()
      req.method = method
      req.uri = url
      req.version = "HTTP/1.1"
      if (body != null && body.length() > 0) {
        headers.add(_root_ide_package_.io.vproxy.base.processor.http1.entity.Header("content-length", "" + body.length()))
      } else {
        headers.add(_root_ide_package_.io.vproxy.base.processor.http1.entity.Header("content-length", "0"))
      }
      req.headers = headers
      req.body = body
      conn.write(req.toByteArray())
    }

    override suspend fun sendHeadersBeforeChunks() {
      val req = _root_ide_package_.io.vproxy.base.processor.http1.entity.Request()
      req.method = method
      req.uri = url
      req.version = "HTTP/1.1"
      headers.add(_root_ide_package_.io.vproxy.base.processor.http1.entity.Header("transfer-encoding", "chunked"))
      req.headers = headers
      conn.write(req.toByteArray())
    }
  }

  /**
   * @return a full response object including body or chunks/trailers.
   * If eof received, an IOException would be thrown instead of returning null Response
   */
  suspend fun readResponse(): _root_ide_package_.io.vproxy.base.processor.http1.entity.Response {
    val parser = _root_ide_package_.io.vproxy.base.http.HttpRespParser(true)
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
    fun simpleGet(full: String): _root_ide_package_.io.vproxy.base.util.promise.Promise<_root_ide_package_.io.vproxy.base.util.ByteArray> {
      val invokerLoop = _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.current()

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

      val loop: _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop
      if (invokerLoop == null) {
        loop = _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.open()
        loop.ensureNetEventLoop()
        loop.loop { _root_ide_package_.io.vproxy.base.util.thread.VProxyThread.create(it, "http1-simple-get") }
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
        val ret: _root_ide_package_.io.vproxy.base.util.ByteArray
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
      if (_root_ide_package_.io.vproxy.vfd.IP.isIpLiteral(x)) {
        return null
      }
      if (x.contains(":")) {
        x = x.substring(0, x.lastIndexOf(":"))
      }
      if (_root_ide_package_.io.vproxy.vfd.IP.isIpLiteral(x)) {
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
      if (_root_ide_package_.io.vproxy.vfd.IP.isIpLiteral(hostAndPort)) {
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
      val ip = if (_root_ide_package_.io.vproxy.vfd.IP.isIpLiteral(host)) {
        _root_ide_package_.io.vproxy.vfd.IP.from(host)
      } else {
        suspendCancellableCoroutine { cont ->
          _root_ide_package_.io.vproxy.base.dns.Resolver.getDefault().resolve(host, object : _root_ide_package_.io.vproxy.base.util.callback.Callback<_root_ide_package_.io.vproxy.vfd.IP, UnknownHostException>() {
            override fun onSucceeded(value: _root_ide_package_.io.vproxy.vfd.IP) {
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
        val sslContext = _root_ide_package_.io.vproxy.base.util.ringbuffer.SSLUtils.getDefaultClientSSLContext()
        val engine: SSLEngine
        if (_root_ide_package_.io.vproxy.vfd.IP.isIpLiteral(host)) {
          engine = sslContext.createSSLEngine()
        } else {
          engine = sslContext.createSSLEngine(host, port)
        }
        return create(_root_ide_package_.io.vproxy.vfd.IPPort(ip, port), engine)
      } else {
        return create(_root_ide_package_.io.vproxy.vfd.IPPort(ip, port))
      }
    }

    suspend fun create(ipport: _root_ide_package_.io.vproxy.vfd.IPPort, engine: SSLEngine): CoroutineHttp1ClientConnection {
      engine.useClientMode = true
      val pair = _root_ide_package_.io.vproxy.base.util.ringbuffer.SSLUtils.genbuf(
        engine, _root_ide_package_.io.vproxy.base.util.RingBuffer.allocate(24576), _root_ide_package_.io.vproxy.base.util.RingBuffer.allocate(24576),
        _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.current(), ipport
      )
      val conn = unsafeIO {
        _root_ide_package_.io.vproxy.base.connection.ConnectableConnection.create(
          ipport, _root_ide_package_.io.vproxy.base.connection.ConnectionOpts(),
          pair.left, pair.right
        ).coroutine()
      }
      conn.connect()
      return conn.asHttp1ClientConnection()
    }

    suspend fun create(ipport: _root_ide_package_.io.vproxy.vfd.IPPort): CoroutineHttp1ClientConnection {
      val conn = unsafeIO {
        _root_ide_package_.io.vproxy.base.connection.ConnectableConnection.create(
          ipport, _root_ide_package_.io.vproxy.base.connection.ConnectionOpts(),
          _root_ide_package_.io.vproxy.base.util.RingBuffer.allocate(16384), _root_ide_package_.io.vproxy.base.util.RingBuffer.allocate(16384)
        ).coroutine()
      }
      conn.connect()
      return conn.asHttp1ClientConnection()
    }
  }
}
