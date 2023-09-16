package io.vproxy.lib.http

import vjson.JSON
import io.vproxy.lib.http.route.SubPath
import io.vproxy.lib.http.route.WildcardSubPath

@Suppress("unused")
interface StorageKey<T>

interface HttpServerConnection {
  fun base(): io.vproxy.base.connection.Connection
  fun response(status: Int): HttpServerResponse
}

interface HttpHeaders {
  fun get(name: String): String?
}

interface HttpServerRequest {
  fun method(): String
  fun uri(): String
  fun query(): Map<String, String>
  fun headers(): HttpHeaders
  fun body(): io.vproxy.base.util.ByteArray
}

interface HttpServerResponse {
  fun header(key: String, value: String): HttpServerResponse
  suspend fun send(body: io.vproxy.base.util.ByteArray?)
  fun isHeadersSent():Boolean
  suspend fun sendHeadersBeforeChunks()
  suspend fun sendChunk(payload: io.vproxy.base.util.ByteArray): HttpServerResponse
  suspend fun endChunks(trailers: List<io.vproxy.base.processor.http1.entity.Header>)

  suspend fun send() = send(null)
  suspend fun send(body: String) = send(io.vproxy.base.util.ByteArray.from(body))
  suspend fun send(json: JSON.Instance<*>) = send(io.vproxy.base.util.ByteArray.from(json.stringify()))
  suspend fun sendChunk(payload: String): HttpServerResponse = sendChunk(io.vproxy.base.util.ByteArray.from(payload))
  suspend fun sendChunk(json: JSON.Instance<*>): HttpServerResponse = sendChunk(io.vproxy.base.util.ByteArray.from(json.stringify()))
}

class RoutingContext(
  val conn: HttpServerConnection,
  val req: HttpServerRequest,
  routes: Map<HttpMethod, io.vproxy.base.util.coll.Tree<SubPath, RoutingHandler>>,
) {
  private var tree: io.vproxy.base.util.coll.Tree<SubPath, RoutingHandler>
  private val uri = req.uri().split("/").map { it.trim() }.filter { it.isNotEmpty() }
  private var handled = false

  private val storage: MutableMap<StorageKey<*>, Any?> = HashMap()
  private val params: MutableMap<String, String> = HashMap()

  init {
    val method = HttpMethod.valueOf(req.method())
    tree = routes[method]!!
  }

  fun <T> put(key: StorageKey<T>, value: T?) {
    storage[key] = value
  }

  @Suppress("unchecked_cast")
  fun <T> get(key: StorageKey<T>): T? {
    return storage[key] as T?
  }

  fun putParam(key: String, value: String): RoutingContext {
    params[key] = value
    return this
  }

  fun param(key: String): String {
    return params[key]!!
  }

  private suspend fun send404() {
    conn.response(404).send("Cannot ${req.method()} ${req.uri()}\r\n")
  }

  private suspend fun send500(e: Throwable) {
    conn.response(500).send("${req.method()} ${req.uri()} failed: $e\r\n")
  }

  suspend fun execute() {
    if (uri.isEmpty()) { // special handle for `/`
      handleLeaves(tree)
    } else {
      executeDFS(tree, 0)
    }

    if (!handled) {
      send404()
    }
  }

  private var nextAllowed: Boolean = false
  fun allowNext() {
    nextAllowed = true
  }

  // return true if the handling must be stopped immediately
  private suspend fun handleLeaves(tree: io.vproxy.base.util.coll.Tree<SubPath, RoutingHandler>) {
    for (handler in tree.leafData()) {
      try {
        handler.handle(this)
      } catch (e: Throwable) {
        io.vproxy.base.util.Logger.error(
          io.vproxy.base.util.LogType.IMPROPER_USE,
          "handler thrown error when handling ${req.method()} ${req.uri()}",
          e
        )
        handled = true
        send500(e)
        return
      }
      val nextAllowed = this.nextAllowed
      this.nextAllowed = false
      if (!nextAllowed) {
        handled = true
        return
      }
    }
  }

  // return true if the handling must be stopped immediately
  private suspend fun executeDFS(tree: io.vproxy.base.util.coll.Tree<SubPath, RoutingHandler>, idx: Int) {
    if (idx >= uri.size) {
      return
    }
    for (br in tree.branches()) {
      if (br.data.match(uri[idx])) {
        val subRoute = if (idx == uri.size - 1) {
          if (uri[idx].contains("?")) {
            uri[idx].substring(0, uri[idx].indexOf("?"))
          } else {
            uri[idx]
          }
        } else {
          uri[idx]
        }
        br.data.fill(this, subRoute)
        if (idx == uri.size - 1 || br.data is WildcardSubPath) {
          handleLeaves(br)
          if (handled) {
            return
          }
        }
        executeDFS(br, idx + 1)
        if (handled) {
          return
        }
      }
    }
  }
}

interface RoutingHandler {
  suspend fun handle(rctx: RoutingContext)
}

typealias RoutingHandlerFunc = suspend (RoutingContext) -> Unit
