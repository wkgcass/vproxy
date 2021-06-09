package vproxy.app.controller

import vjson.JSON
import vjson.util.ObjectBuilder
import vproxy.app.app.Application
import vproxy.base.Config
import vproxy.base.connection.ServerSock
import vproxy.base.util.LogType
import vproxy.base.util.Logger
import vproxy.lib.common.fitCoroutine
import vproxy.lib.common.launch
import vproxy.lib.http.RoutingContext
import vproxy.lib.http.Tool
import vproxy.lib.http1.CoroutineHttp1Server
import vproxy.vfd.UDSPath
import java.util.*

@Suppress("DuplicatedCode")
class DockerNetworkPluginController(val alias: String, val path: UDSPath) {
  companion object {
    private const val dockerNetworkPluginBase = "/"
    private val driver: DockerNetworkDriver = DockerNetworkDriverImpl()
  }

  private val server: CoroutineHttp1Server

  init {
    val loop = Application.get().controlEventLoop

    // prepare
    if (Config.checkBind) {
      try {
        Thread.sleep(1000)
        // sleep for a while, maybe the old process would exit
      } catch (ignore: InterruptedException) {
      }
      // no need to check explicitly, because uds cannot enable reuse_port
    }
    val sock = ServerSock.create(path).fitCoroutine(loop)
    server = CoroutineHttp1Server(sock)

    // see https://github.com/moby/libnetwork/blob/master/docs/remote.md
    server.post("$dockerNetworkPluginBase/*", Tool.bodyJsonHandler())
    server.post("$dockerNetworkPluginBase/Plugin.Activate") { rctx: RoutingContext -> handshake(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.GetCapabilities") { rctx: RoutingContext -> capabilities(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.CreateNetwork") { rctx: RoutingContext -> createNetwork(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.DeleteNetwork") { rctx: RoutingContext -> deleteNetwork(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.CreateEndpoint") { rctx: RoutingContext -> createEndpoint(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.EndpointOperInfo") { rctx: RoutingContext -> endpointOperationalInfo(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.DeleteEndpoint") { rctx: RoutingContext -> deleteEndpoint(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.Join") { rctx: RoutingContext -> join(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.Leave") { rctx: RoutingContext -> leave(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.DiscoverNew") { rctx: RoutingContext -> discoverNew(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.DiscoverDelete") { rctx: RoutingContext -> discoverDelete(rctx) }

    // start
    loop.selectorEventLoop.launch {
      server.start()
    }
  }

  fun stop() {
    server.close()
  }

  private suspend fun handshake(rctx: RoutingContext) {
    rctx.conn.response(200).send(
      ObjectBuilder()
        .putArray("Implements") { add("NetworkDriver") }
        .build()
    )
  }

  private suspend fun capabilities(rctx: RoutingContext) {
    rctx.conn.response(200).send(
      ObjectBuilder()
        .put("Scope", "local")
        .put("ConnectivityScope", "local")
        .build()
    )
  }

  private fun err(err: String?): JSON.Object {
    return ObjectBuilder()
      .put("Err", err)
      .build()
  }

  private suspend fun createNetwork(rctx: RoutingContext) {
    val req = DockerNetworkDriver.CreateNetworkRequest()
    try {
      val body = rctx.get(Tool.bodyJson) as JSON.Object
      req.networkId = body.getString("NetworkID")
      req.ipv4Data = LinkedList()
      val ipv4Data = body.getArray("IPv4Data")
      parseIPData(req.ipv4Data, ipv4Data)
      req.ipv6Data = LinkedList()
      val ipv6Data = body.getArray("IPv6Data")
      parseIPData(req.ipv6Data, ipv6Data)
    } catch (e: RuntimeException) {
      Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send(err("invalid request body"))
      return
    }
    try {
      driver.createNetwork(req)
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to create network", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    rctx.conn.response(200).send(ObjectBuilder().build())
    return
  }

  private fun parseIPData(ipv4Data: MutableList<DockerNetworkDriver.IPData>, raw: JSON.Array) {
    for (i in 0 until raw.length()) {
      val obj = raw.getObject(i)
      val data = DockerNetworkDriver.IPData()
      data.addressSpace = obj.getString("AddressSpace")
      data.pool = obj.getString("Pool")
      data.gateway = obj.getString("Gateway")
      if (obj.containsKey("AuxAddresses")) {
        data.auxAddresses = HashMap()
        val auxAddresses = obj.getObject("AuxAddresses")
        for (key in auxAddresses.keySet()) {
          data.auxAddresses[key] = auxAddresses.getString(key)
        }
      }
      ipv4Data.add(data)
    }
  }

  private suspend fun deleteNetwork(rctx: RoutingContext) {
    val networkId: String
    try {
      networkId = (rctx.get(Tool.bodyJson) as JSON.Object).getString("NetworkID")
    } catch (e: RuntimeException) {
      Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send(err("invalid request body"))
      return
    }
    try {
      driver.deleteNetwork(networkId)
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to delete network", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    rctx.conn.response(200).send(ObjectBuilder().build())
  }

  private suspend fun createEndpoint(rctx: RoutingContext) {
    val req = DockerNetworkDriver.CreateEndpointRequest()
    try {
      val body = rctx.get(Tool.bodyJson) as JSON.Object
      req.networkId = body.getString("NetworkID")
      req.endpointId = body.getString("EndpointID")
      if (body.containsKey("Interface")) {
        val interf = body.getObject("Interface")
        req.netInterface = DockerNetworkDriver.NetInterface()
        req.netInterface.address = interf.getString("Address")
        req.netInterface.addressIPV6 = interf.getString("AddressIPv6")
        req.netInterface.macAddress = interf.getString("MacAddress")
      }
    } catch (e: RuntimeException) {
      Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send(err("invalid request body"))
      return
    }
    val resp: DockerNetworkDriver.CreateEndpointResponse
    try {
      resp = driver.createEndpoint(req)
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to create endpoint", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    if (resp.netInterface == null) {
      rctx.conn.response(200).send(ObjectBuilder().build())
    } else {
      rctx.conn.response(200).send(
        ObjectBuilder()
          .putObject("Interface") {
            put("Address", resp.netInterface.address)
            put("AddressIPv6", resp.netInterface.addressIPV6)
            put("MacAddress", resp.netInterface.macAddress)
          }
          .build()
      )
    }
  }

  private suspend fun endpointOperationalInfo(rctx: RoutingContext) {
    rctx.conn.response(200).send(ObjectBuilder().putObject("Value") { }.build())
  }

  private suspend fun deleteEndpoint(rctx: RoutingContext) {
    val networkId: String
    val endpointId: String
    try {
      val body = rctx.get(Tool.bodyJson) as JSON.Object
      networkId = body.getString("NetworkID")
      endpointId = body.getString("EndpointID")
    } catch (e: RuntimeException) {
      Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send("invalid request body")
      return
    }
    try {
      driver.deleteEndpoint(networkId, endpointId)
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to delete endpoint", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    rctx.conn.response(200).send(ObjectBuilder().build())
  }

  private suspend fun join(rctx: RoutingContext) {
    val networkId: String
    val endpointId: String
    val sandboxKey: String
    try {
      val body = rctx.get(Tool.bodyJson) as JSON.Object
      networkId = body.getString("NetworkID")
      endpointId = body.getString("EndpointID")
      sandboxKey = body.getString("SandboxKey")
    } catch (e: RuntimeException) {
      Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send("invalid request body")
      return
    }
    val resp: DockerNetworkDriver.JoinResponse
    try {
      resp = driver.join(networkId, endpointId, sandboxKey)
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to join", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    rctx.conn.response(200).send(ObjectBuilder()
      .putObject("InterfaceName") {
        put("SrcName", resp.interfaceName.srcName)
        put("DstPrefix", resp.interfaceName.dstPrefix)
      }
      .put("Gateway", resp.gateway)
      .put("GatewayIPv6", resp.gatewayIPv6)
      .putArray("StaticRoutes") {
        for (x in resp.staticRoutes) {
          addObject {
            put("Destination", x.destination)
            put("RouteType", x.routeType)
            if (x.nextHop != null) {
              put("NextHop", x.nextHop)
            }
          }
        }
      }
      .build()
    )
  }

  private suspend fun leave(rctx: RoutingContext) {
    val networkId: String
    val endpointId: String
    try {
      val body = rctx.get(Tool.bodyJson) as JSON.Object
      networkId = body.getString("NetworkID")
      endpointId = body.getString("EndpointID")
    } catch (e: RuntimeException) {
      Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send("invalid request body")
      return
    }
    try {
      driver.leave(networkId, endpointId)
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to leave", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    rctx.conn.response(200).send(ObjectBuilder().build())
  }

  private suspend fun discoverNew(rctx: RoutingContext) {
    // TODO do not care about this event, this driver only work on local for now
    rctx.conn.response(200).send(ObjectBuilder().build())
  }

  private suspend fun discoverDelete(rctx: RoutingContext) {
    // TODO do not care about this event, this driver only work on local for now
    rctx.conn.response(200).send(ObjectBuilder().build())
  }
}
