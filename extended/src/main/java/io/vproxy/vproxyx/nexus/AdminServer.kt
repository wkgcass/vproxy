package io.vproxy.vproxyx.nexus

import io.vproxy.base.connection.ServerSock
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.callback.BlockCallback
import io.vproxy.lib.common.launch
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.lib.tcp.CoroutineServerSock
import io.vproxy.vfd.IPPort
import io.vproxy.vproxyx.nexus.entity.NexusConfiguration
import io.vproxy.vproxyx.nexus.entity.ProxyInstance
import vjson.CharStream
import vjson.JSON
import vjson.parser.ParserOptions
import vjson.util.ArrayBuilder
import vjson.util.ObjectBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.system.exitProcess

class AdminServer(private val nctx: NexusContext, port: Int) {
  private val app: CoroutineHttp1Server

  init {
    val sock = ServerSock.create(IPPort("127.0.0.1", port))
    val cosock = CoroutineServerSock(nctx.loop, sock)
    app = CoroutineHttp1Server(cosock)
    app.get("/apis/v1.0/proxies", ::listProxy)
    app.post("/apis/v1.0/proxies", ::addProxy)
    app.del("/apis/v1.0/proxies/:id", ::deleteProxy)
    app.get("/apis/v1.0/graph", ::getGraph)
  }

  fun start() {
    nctx.loop.selectorEventLoop.launch {
      app.start()
    }
  }

  private suspend fun listProxy(ctx: RoutingContext) {
    val body = ArrayBuilder()
    for (proxy in nctx.resources.proxies) {
      body.addInst(proxy.toJson())
    }
    ctx.conn.response(200).send(body.build())
  }

  private suspend fun addProxy(ctx: RoutingContext) {
    val body = ctx.req.body().toString()
    val proxy = JSON.deserialize(body, ProxyInstance.rule)
    if (proxy.id != null) {
      ctx.conn.response(400).send(
        ObjectBuilder()
          .put("code", 400)
          .put("message", "`id` should not be specified")
          .build()
      )
      return
    }
    doAddProxy(proxy) { code, respBody ->
      ctx.conn.response(code).send(respBody)
    }
  }

  private suspend fun doAddProxy(proxy: ProxyInstance, callback: suspend (Int, JSON.Object) -> Unit) {
    proxy.id = UUID.randomUUID().toString()

    if (proxy.node.isNullOrBlank()) {
      return callback(
        400, ObjectBuilder()
          .put("code", 400)
          .put("message", "`node` is not specified")
          .build()
      )
    }
    proxy.node = proxy.node.trim()

    if (proxy.listen < 1 || proxy.listen > 65535) {
      return callback(
        400, ObjectBuilder()
          .put("code", 400)
          .put("message", "`listen` is not a valid port number: ${proxy.listen}")
          .build()
      )
    }

    if (proxy.target == null) {
      return callback(
        400, ObjectBuilder()
          .put("code", 400)
          .put("message", "`target` is not specified")
          .build()
      )
    }

    try {
      ServerSock.checkBind(IPPort("127.0.0.1", proxy.listen))
    } catch (e: Exception) {
      return callback(
        409, ObjectBuilder()
          .put("code", 409)
          .put("message", "port ${proxy.listen} is already in use")
          .build()
      )
    }

    val sock = ServerSock.create(IPPort("127.0.0.1", proxy.listen))
    proxy.serverSock = sock
    nctx.resources.putProxy(proxy)
    Logger.access("proxy is created: $proxy")
    callback(200, proxy.toJson())

    StreamHandlers.handleServerSock(nctx, proxy.target, proxy.node, sock)
  }

  private suspend fun deleteProxy(ctx: RoutingContext) {
    val id = ctx.param("id")
    val proxy = nctx.resources.removeProxy(id)
    if (proxy == null) {
      ctx.conn.response(404).send("proxy with id $id not found")
      return
    } else {
      Logger.access("proxy is removed: $proxy")
      ctx.conn.response(204).send()
    }
  }

  private suspend fun getGraph(ctx: RoutingContext) {
    ctx.conn.response(200).send(nctx.nexus.toMermaidString())
  }

  fun loadConfigOrExit(path: String) {
    val content = Files.readString(Path.of(path))
    val conf = JSON.deserialize(CharStream.Companion.from(content), NexusConfiguration.rule, ParserOptions.allFeatures())

    data class Statistics(var proxies: Int = 0)

    val cb = BlockCallback<Statistics, Throwable>()
    nctx.loop.selectorEventLoop.launch {
      val statistics = Statistics()
      if (conf.proxies != null) {
        for (p in conf.proxies) {
          doAddProxy(p) { code, body -> if (code != 200) cb.failed(Exception(body.stringify())) }
          statistics.proxies += 1
        }
      }
      cb.succeeded(statistics)
    }
    val statistics = try {
      cb.block()
    } catch (e: Throwable) {
      Logger.error(LogType.INVALID_EXTERNAL_DATA, "failed to load config", e)
      exitProcess(1)
    }
    Logger.alert("configuration is loaded from $path: $statistics")
  }
}
