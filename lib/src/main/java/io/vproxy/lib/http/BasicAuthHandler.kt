package io.vproxy.lib.http

import java.nio.charset.StandardCharsets
import java.util.*

class BasicAuthHandler(secret: String?) : RoutingHandler {

  private var credentials: String? = null

    init {
    if (!secret.isNullOrBlank()) {
      credentials = "Basic " + Base64.getEncoder().encodeToString(secret.toByteArray(StandardCharsets.UTF_8))
    }
  }

  override suspend fun handle(rctx: RoutingContext) {
    if (credentials.isNullOrBlank()) {
      rctx.allowNext()
      return
    } else {
      val authorization = rctx.req.headers().get("Authorization")
      if (!credentials.equals(authorization)) {
        val rsp = rctx.conn.response(401)
        rsp.header("WWW-Authenticate", "Basic")
        rsp.send()
        return
      }
      rctx.allowNext()
    }
  }
}