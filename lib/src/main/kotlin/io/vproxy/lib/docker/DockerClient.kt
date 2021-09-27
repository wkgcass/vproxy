package vproxy.lib.docker

import vjson.JSON
import vproxy.base.connection.ConnectableConnection
import vproxy.base.connection.NetEventLoop
import vproxy.lib.common.ByteArrayCharStream
import vproxy.lib.common.vplib
import vproxy.lib.docker.entity.Network
import vproxy.lib.http1.CoroutineHttp1ClientConnection
import vproxy.lib.tcp.CoroutineConnection
import vproxy.vfd.UDSPath
import java.io.IOException
import java.nio.charset.StandardCharsets

class DockerClient(val loop: NetEventLoop, val sock: String = "/var/run/docker.sock", val version: String = "") {
  private val sockUds = UDSPath(sock)
  var timeout = 5000

  @Suppress("SameParameterValue")
  private fun formatUrl(url: String): String {
    return if (version == "") {
      url
    } else {
      "/v$version/$url"
    }
  }

  suspend fun listNetworks(): List<Network> {
    @Suppress("BlockingMethodInNonBlockingContext")
    val tcpConn = CoroutineConnection(loop, ConnectableConnection.create(sockUds))
    return vplib.coroutine.with(tcpConn).run {
      tcpConn.setTimeout(timeout)
      tcpConn.connect()
      val conn = CoroutineHttp1ClientConnection(tcpConn)
      val req = conn.request("GET", formatUrl("/networks"))
      req.header("Host", "localhost")
      req.send()
      val resp = conn.readResponse()
      if (resp.statusCode != 200) {
        throw IOException("response status is not 200: $resp")
      }
      try {
        JSON.deserialize(ByteArrayCharStream(resp.getBody(), StandardCharsets.UTF_8), Network.arrayRule)
      } catch (e: RuntimeException) {
        throw IOException("invalid body: $resp", e)
      }
    }
  }
}
