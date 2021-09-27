package io.vproxy.vproxyx.websocks.relay

import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.with
import io.vproxy.lib.http.RoutingHandlerFunc
import io.vproxy.lib.http1.CoroutineHttp1Server
import java.io.IOException

object RelayHttpServer {
  @JvmStatic
  @Throws(IOException::class)
  fun launch(worker: io.vproxy.base.component.elgroup.EventLoopGroup): CoroutineHttp1Server {
    val sock = io.vproxy.base.connection.ServerSock.create(io.vproxy.vfd.IPPort("0.0.0.0", 80))
    val loop = worker.next()
    val cosock = sock.coroutine(loop)
    val server = CoroutineHttp1Server(cosock)
    val handler: RoutingHandlerFunc = {
      var host: String? = it.req.headers().get("host")
      if (host != null) {
        if (host.contains(":")) {
          host = host.substring(0, host.indexOf(":"))
        }
      }
      if (host != null && io.vproxy.vfd.IP.isIpLiteral(host)) {
        host = null
      }
      if (host == null || host.isBlank()) {
        val respBody: String = io.vproxy.base.util.web.ErrorPages.build(
          "VPROXY ERROR PAGE",
          "Cannot handle the request",
          "no `Host` header available, or `Host` header is ip"
        )
        it.conn.response(400).header("Connection", "Close").send(respBody)
      } else {
        val newUrl = "https://" + host + it.req.uri()
        it.conn.response(302).header("Location", newUrl).header("Connection", "Close").send()
      }
    }
    server.get("/*", handler)
    server.get("/", handler)

    loop.selectorEventLoop.with(server).launch {
      server.start()
    }
    return server
  }
}
