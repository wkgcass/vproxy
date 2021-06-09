package vproxy.poc

import vjson.JSON
import vjson.util.ObjectBuilder
import vjson.util.Transformer
import vproxy.base.connection.ConnectableConnection
import vproxy.base.connection.ServerSock
import vproxy.base.processor.http1.entity.Response
import vproxy.base.selector.SelectorEventLoop
import vproxy.base.util.ByteArray
import vproxy.base.util.Logger
import vproxy.base.util.kt.KT
import vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.fitCoroutine
import vproxy.lib.common.launch
import vproxy.lib.common.sleep
import vproxy.lib.http.RoutingContext
import vproxy.lib.http.Tool
import vproxy.lib.http1.CoroutineHttp1Server
import vproxy.vfd.IP
import vproxy.vfd.IPPort
import java.util.*

@Suppress("BlockingMethodInNonBlockingContext")
class ApplicationLevelHttpServer {
  private class Service constructor(val name: String, var address: String, var port: Int) {
    val id: UUID = UUID.randomUUID()
  }

  private val services: MutableList<Service> = LinkedList<Service>()
  private val tf = Transformer()
    .addRule(KT.kclass(Service::class.java)) { s: Service ->
      ObjectBuilder()
        .put("id", s.id.toString())
        .put("name", s.name)
        .put("ingressAddress", s.address)
        .put("ingressPort", s.port)
        .build()
    }

  private fun runClient() {
    val loop = SelectorEventLoop.open()
    loop.ensureNetEventLoop()
    loop.loop { VProxyThread.create(it, "app-level-http-run-client") }
    val conn = ConnectableConnection.create(IPPort("127.0.0.1", 8080)).fitCoroutine(loop.ensureNetEventLoop())
    loop.launch {
      sleep(500)
      conn.connect()
      val http = conn.asHttp1ClientConnection()
      http.post("/api/v1/services").send(
        ObjectBuilder()
          .put("name", "myservice1")
          .put("address", "192.168.0.1")
          .put("port", 80)
          .build()
      )
      http.post("/api/v1/services").send(
        ObjectBuilder()
          .put("name", "myservice2")
          .put("address", "192.168.0.2")
          .put("port", 80)
          .build()
      )
      var resp = http.readResponse()
      printResponse("myservice1", resp)
      resp = http.readResponse()
      printResponse("myservice2", resp)

      http.get("/api/v1/services").send()
      resp = http.readResponse()
      if (resp.body == null) {
        resp.body = ByteArray.from("{}")
      }
      println("Fetch services result:")
      println(JSON.parse(resp.body.toString()))
    }.invokeOnCompletion {
      conn.close()
    }
  }

  private fun printResponse(name: String, resp: Response) {
    if (resp.statusCode != 200) {
      println("Request failed for " + name + ": " + resp.body)
    } else {
      println("Request succeeded for $name")
    }
  }

  private fun runServer() {
    val loop = SelectorEventLoop.open()
    loop.ensureNetEventLoop()
    loop.loop { VProxyThread.create(it, "app-level-http-run-server") }
    val svrsock = ServerSock.create(IPPort("::", 8080))
    val server = CoroutineHttp1Server(svrsock.fitCoroutine(loop.ensureNetEventLoop()))
    server
      .all("/*") { log(it) }
      .all("/api/v1/*", Tool.bodyJsonHandler())
      .get("/api/v1/services/:serviceId") { getService(it) }
      .get("/api/v1/services") { listServices(it) }
      .post("/api/v1/services") { createService(it) }
      .put("/api/v1/services/:serviceId") { updateService(it) }
      .del("/api/v1/services/:serviceId") { deleteService(it) }

    loop.launch {
      server.start()
    }
  }

  private fun log(rctx: RoutingContext) {
    Logger.alert(
      "received request remote=" + rctx.conn.base().remote.formatToIPPortString()
        .toString() + " -> local=" + rctx.conn.base().local.formatToIPPortString()
        .toString() + " " + rctx.req.method() + " " + rctx.req.uri()
    )
    rctx.allowNext()
  }

  private suspend fun listServices(rctx: RoutingContext) {
    Logger.alert("listServices called")
    rctx.conn.response(200).send(tf.transform(services))
  }

  private suspend fun createService(rctx: RoutingContext) {
    val body: JSON.Instance<*> = rctx.get(Tool.bodyJson)!!
    Logger.alert("listServices called with $body")
    val service: Service
    try {
      val o = body as JSON.Object
      val name = o.getString("name")
      val address = o.getString("address")
      val port = o.getInt("port")
      require(IP.isIpLiteral(address))
      require(!(port < 1 || port > 65535))
      service = Service(name, address, port)
      services.add(service)
    } catch (e: RuntimeException) {
      rctx.conn.response(400)
        .send(
          ObjectBuilder()
            .put("message", "invalid request body")
            .build()
        )
      return
    }
    rctx.conn.response(200).send(tf.transform(service))
  }

  private suspend fun getService(rctx: RoutingContext) {
    val serviceId: String = rctx.param("serviceId")
    Logger.alert("getService called with `$serviceId`")
    val ret = services.stream().filter { s: Service -> s.id.toString() == serviceId }.findAny()
    if (ret.isPresent) {
      rctx.conn.response(200).send(tf.transform(ret.get()))
    } else {
      rctx.conn.response(404)
        .send(
          ObjectBuilder()
            .put("message", "service with id `$serviceId` not found")
            .build()
        )
    }
  }

  private suspend fun updateService(rctx: RoutingContext) {
    val serviceId: String = rctx.param("serviceId")
    val body: JSON.Instance<*> = rctx.get(Tool.bodyJson)!!
    Logger.alert("updateService called with `$serviceId` and $body")
    var address: String? = null
    var port = -1
    try {
      val o = body as JSON.Object
      if (o.containsKey("address")) {
        address = o.getString("address")
        require(IP.isIpLiteral(address))
      }
      if (o.containsKey("port")) {
        port = o.getInt("port")
        require(!(port < 1 || port > 65535))
      }
    } catch (e: RuntimeException) {
      rctx.conn.response(400)
        .send(
          ObjectBuilder()
            .put("message", "invalid request body")
            .build()
        )
      return
    }
    val ret = services.stream().filter { s: Service -> s.id.toString() == serviceId }.findAny()
    if (ret.isPresent) {
      val s = ret.get()
      if (address != null) {
        s.address = address
      }
      if (port != -1) {
        s.port = port
      }
      rctx.conn.response(204).send()
    } else {
      rctx.conn.response(404)
        .send(
          ObjectBuilder()
            .put("message", "service with id `$serviceId` not found")
            .build()
        )
    }
  }

  private suspend fun deleteService(rctx: RoutingContext) {
    val serviceId: String = rctx.param("serviceId")
    Logger.alert("deleteService called with `$serviceId`")
    val ret = services.stream().filter { s: Service -> s.id.toString() == serviceId }.findAny()
    if (ret.isPresent) {
      services.remove(ret.get())
      rctx.conn.response(204).send()
    } else {
      rctx.conn.response(404)
        .send(
          ObjectBuilder()
            .put("message", "service with id `$serviceId` not found")
            .build()
        )
    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      println("Start server")
      ApplicationLevelHttpServer().runServer()
      Thread.sleep(1000)
      println("Start client to build initial data")
      ApplicationLevelHttpServer().runClient()
    }
  }
}
