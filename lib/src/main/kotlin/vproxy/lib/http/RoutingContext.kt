package vproxy.lib.http

import vjson.JSON
import vproxy.base.connection.Connection
import vproxy.base.processor.http1.entity.Header
import vproxy.base.util.ByteArray
import vproxy.base.util.LogType
import vproxy.base.util.Logger
import vproxy.base.util.Tree
import vproxy.lib.http.route.SubPath
import vproxy.lib.http.route.WildcardSubPath

@Suppress("unused")
interface StorageKey<T>

interface HttpServerConnection {
  fun base(): Connection
  fun response(status: Int): HttpServerResponse
}

interface HttpHeaders {
  fun get(name: String): String?
}

interface HttpServerRequest {
  fun method(): String
  fun uri(): String
  fun headers(): HttpHeaders
  fun body(): ByteArray
}

interface HttpServerResponse {
  fun header(key: String, value: String): HttpServerResponse
  suspend fun send(body: ByteArray?)
  suspend fun sendHeadersBeforeChunks()
  suspend fun sendChunk(payload: ByteArray): HttpServerResponse
  suspend fun endChunks(trailers: List<Header>)

  suspend fun send() = send(null)
  suspend fun send(body: String) = send(ByteArray.from(body))
  suspend fun send(json: JSON.Instance<*>) = send(ByteArray.from(json.stringify()))
  suspend fun sendChunk(payload: String): HttpServerResponse = sendChunk(ByteArray.from(payload))
  suspend fun sendChunk(json: JSON.Instance<*>): HttpServerResponse = sendChunk(ByteArray.from(json.stringify()))
}

class RoutingContext(
  val conn: HttpServerConnection,
  val req: HttpServerRequest,
  routes: Map<HttpMethod, Tree<SubPath, RoutingHandler>>,
) {
  private var tree: Tree<SubPath, RoutingHandler>
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
  private suspend fun handleLeaves(tree: Tree<SubPath, RoutingHandler>) {
    for (handler in tree.leafData()) {
      try {
        handler.handle(this)
      } catch (e: Throwable) {
        Logger.error(LogType.IMPROPER_USE, "handler thrown error when handling ${req.method()} ${req.uri()}", e)
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
  private suspend fun executeDFS(tree: Tree<SubPath, RoutingHandler>, idx: Int) {
    if (idx >= uri.size) {
      return
    }
    for (br in tree.branches()) {
      if (br.data.match(uri[idx])) {
        br.data.fill(this, uri[idx])
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
