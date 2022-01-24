package io.vproxy.app.controller

import io.vproxy.base.util.Network
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.launch
import io.vproxy.lib.common.sleep
import io.vproxy.lib.common.vplib
import io.vproxy.lib.docker.DockerClient
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http.Tool
import io.vproxy.lib.http1.CoroutineHttp1Server
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.system.exitProcess

@Suppress("DuplicatedCode")
class DockerNetworkPluginController(val path: io.vproxy.vfd.UDSPath, requireSync: Boolean) {
  companion object {
    private const val dockerNetworkPluginBase = ""
    private val driver: DockerNetworkDriver =
      io.vproxy.app.controller.DockerNetworkDriverImpl()
  }

  private val server: CoroutineHttp1Server
  private var ready = !requireSync
  private var syncing = false
  private var scheduled = false

  init {
    val loop = io.vproxy.app.app.Application.get().controlEventLoop

    // prepare
    if (io.vproxy.base.Config.checkBind) {
      try {
        Thread.sleep(1000)
        // sleep for a while, maybe the old process would exit
      } catch (ignore: InterruptedException) {
      }
      // no need to check explicitly, because uds cannot enable reuse_port
    }
    val sock = io.vproxy.base.connection.ServerSock.create(path).coroutine(loop)
    server = CoroutineHttp1Server(sock)

    // see https://github.com/moby/libnetwork/blob/master/docs/remote.md
    server.post("$dockerNetworkPluginBase/*", Tool.bodyJsonHandler())
    server.post("$dockerNetworkPluginBase/*") { rctx: RoutingContext -> accessLog(rctx) }
    server.post("$dockerNetworkPluginBase/Plugin.Activate") { rctx: RoutingContext -> handshake(rctx) }
    server.post("$dockerNetworkPluginBase/NetworkDriver.GetCapabilities") { rctx: RoutingContext -> capabilities(rctx) }
    server.post("$dockerNetworkPluginBase/*") { rctx: RoutingContext -> routeSyncNetworks(rctx) }
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

  private fun accessLog(rctx: RoutingContext) {
    val body = rctx.get(Tool.bodyJson)
    io.vproxy.base.util.Logger.access("received request: " + rctx.req.method() + " " + rctx.req.uri() + " " + body?.stringify())
    rctx.allowNext()
  }

  private suspend fun routeSyncNetworks(ctx: RoutingContext) {
    scheduleSyncNetworks()
    if (!ready) {
      throw Exception("sync networks failed")
    }
    ctx.allowNext()
  }

  private suspend fun handshake(rctx: RoutingContext) {
    rctx.conn.response(200).send(
      ObjectBuilder()
        .putArray("Implements") { add("NetworkDriver") }
        .build()
    )
  }

  private suspend fun capabilities(rctx: RoutingContext) {
    scheduleSyncNetworks()

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
      req.options = body.getObject("Options")
      req.initGenericOptions()
    } catch (e: RuntimeException) {
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send(err("invalid request body"))
      return
    }
    try {
      driver.createNetwork(req)
    } catch (e: Exception) {
      io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.SYS_ERROR, "failed to create network", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    io.vproxy.app.process.Shutdown.autoSave()
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
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send(err("invalid request body"))
      return
    }
    try {
      driver.deleteNetwork(networkId)
    } catch (e: Exception) {
      io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.SYS_ERROR, "failed to delete network", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    io.vproxy.app.process.Shutdown.autoSave()
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
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send(err("invalid request body"))
      return
    }
    val resp: DockerNetworkDriver.CreateEndpointResponse
    try {
      resp = driver.createEndpoint(req)
    } catch (e: Exception) {
      io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.SYS_ERROR, "failed to create endpoint", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    io.vproxy.app.process.Shutdown.autoSave()
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
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send("invalid request body")
      return
    }
    try {
      driver.deleteEndpoint(networkId, endpointId)
    } catch (e: Exception) {
      io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.SYS_ERROR, "failed to delete endpoint", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    io.vproxy.app.process.Shutdown.autoSave()
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
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send("invalid request body")
      return
    }
    val resp: DockerNetworkDriver.JoinResponse
    try {
      resp = driver.join(networkId, endpointId, sandboxKey)
    } catch (e: Exception) {
      io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.SYS_ERROR, "failed to join", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    io.vproxy.app.process.Shutdown.autoSave()
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
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.INVALID_EXTERNAL_DATA, "invalid request body: ", e)
      rctx.conn.response(200).send("invalid request body")
      return
    }
    try {
      driver.leave(networkId, endpointId)
    } catch (e: Exception) {
      io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.SYS_ERROR, "failed to leave", e)
      rctx.conn.response(200).send(err(e.message))
      return
    }
    io.vproxy.app.process.Shutdown.autoSave()
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

  override fun toString(): String {
    return "docker-network-plugin-controller -> path " + path.path
  }

  private suspend fun scheduleSyncNetworks(retries: Int = 2) {
    if (ready) {
      return
    }
    if (syncing) {
      while (syncing) {
        sleep(500)
      }
      if (ready) {
        return
      }
      scheduleSyncNetworks(0)
      return
    }
    syncing = true

    val client = DockerClient(io.vproxy.base.selector.SelectorEventLoop.current().ensureNetEventLoop())
    client.timeout = 1500
    for (i in 0..retries) {
      if (i == 0) {
        io.vproxy.base.util.Logger.alert("sync networks from docker daemon")
      } else {
        io.vproxy.base.util.Logger.alert("sync networks from docker daemon, already tried count: $i")
      }
      try {
        syncNetworks(client)
      } catch (e: SocketTimeoutException) {
        io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.CONN_ERROR, "requesting docker daemon timed-out: " + io.vproxy.base.util.Utils.formatErr(e))
        continue
      } catch (e: Throwable) {
        io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.SYS_ERROR, "failed to sync networks from docker daemon")
        syncing = false
        throw e
      }
      ready = true
      break
    }

    if (!ready && !scheduled) {
      scheduled = true
      vplib.coroutine.launch {
        val time = 10
        io.vproxy.base.util.Logger.alert("re-sync $time seconds later ...")
        sleep(time * 1000)
        scheduled = false
        if (!syncing) {
          scheduleSyncNetworks()
        }
      }
    }

    syncing = false
  }

  /**
   * request docker daemon for networks
   */
  private suspend fun syncNetworks(client: DockerClient) {
    var cnt = 0
    try {
      val networks = client.listNetworks()
      io.vproxy.base.util.Logger.alert("retrieved docker networks: $networks")
      for (network in networks) {
        if (network.driver == null || network.driver!!.startsWith("vproxyio/docker-plugin:").not()) {
          continue
        }
        val createReq = DockerNetworkDriver.CreateNetworkRequest()
        createReq.networkId = network.id
        createReq.ipv4Data = LinkedList()
        createReq.ipv6Data = LinkedList()
        for (conf in network.ipam!!.config!!) {
          val net = Network.from(conf.subnet!!)
          val data = DockerNetworkDriver.IPData()
          data.addressSpace = "" // not used
          data.pool = net.toString()
          data.gateway = conf.gateway ?: buildGateway(net)
          data.auxAddresses = null // not used
          if (net.ip is io.vproxy.vfd.IPv4) {
            createReq.ipv4Data!!.add(data)
          } else {
            assert(net.ip is io.vproxy.vfd.IPv6)
            createReq.ipv6Data!!.add(data)
          }
        }
        createReq.options = ObjectBuilder()
          .putObject(DockerNetworkDriver.CreateNetworkRequest.optionsDockerNetworkGenericKey) {
            for ((k, v) in network.options!!) {
              put(k, v)
            }
          }
          .build()
        createReq.initGenericOptions()

        io.vproxy.base.util.Logger.alert("creating network: $createReq")
        driver.createNetwork(createReq)
        cnt += 1
      }
      io.vproxy.app.process.Shutdown.autoSave()

      io.vproxy.base.util.Logger.alert("$cnt networks created")
    } catch (e: SocketTimeoutException) {
      throw e
    } catch (e: Throwable) {
      io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.SYS_ERROR, "failed to initiate networks ($cnt networks already created before error)", e)
      try {
        @Suppress("BlockingMethodInNonBlockingContext")
        Files.delete(Path.of(DockerNetworkDriver.TEMPORARY_CONFIG_FILE))
      } catch (e2: Throwable) {
        io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.FILE_ERROR, "failed to rollback " + DockerNetworkDriver.TEMPORARY_CONFIG_FILE, e2)
      }
      exitProcess(1)
      @Suppress("UNREACHABLE_CODE")
      throw e
    }
  }

  private fun buildGateway(net: Network): String {
    val bytes = net.ip.address.copyOf()
    bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
    return io.vproxy.vfd.IP.from(bytes).formatToIPString() + "/" + net.mask
  }
}
