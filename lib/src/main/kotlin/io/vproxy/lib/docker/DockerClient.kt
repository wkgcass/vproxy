package io.vproxy.lib.docker

import io.vproxy.lib.common.ByteArrayCharStream
import io.vproxy.lib.common.vplib
import io.vproxy.lib.docker.entity.Network
import io.vproxy.lib.http1.CoroutineHttp1ClientConnection
import io.vproxy.lib.tcp.CoroutineConnection
import io.vproxy.dep.vjson.JSON
import java.io.IOException
import java.nio.charset.StandardCharsets

class DockerClient(val loop: io.vproxy.base.connection.NetEventLoop, val sock: String = "/var/run/docker.sock", val version: String = "") {
  private val sockUds = io.vproxy.vfd.UDSPath(sock)
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
    val tcpConn = CoroutineConnection(loop, io.vproxy.base.connection.ConnectableConnection.create(sockUds))
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
