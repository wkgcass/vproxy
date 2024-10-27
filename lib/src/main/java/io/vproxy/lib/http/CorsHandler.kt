package io.vproxy.lib.http

/**
 * [CORS docs](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS)
 */
class CorsHandler(private val enable: Boolean) : RoutingHandler {

  override suspend fun handle(rctx: RoutingContext) {
    val req = rctx.req
    val origin = req.headers().get("Origin")
    if (req.method().equals("OPTIONS", true)) {
      if (!enable || origin.isNullOrBlank()) {
        rctx.conn.response(403).send()
      } else {
        val methods = req.headers().get("Access-Control-Request-Method")
        val headers = req.headers().get("Access-Control-Request-Headers")
        val rsp = rctx.conn.response(204)
        rsp.header("Access-Control-Allow-Origin", origin)
        if (null != methods) {
          rsp.header("Access-Control-Allow-Methods", methods)
        }
        if (null != headers) {
          rsp.header("Access-Control-Allow-Headers", headers)
        }
        rsp.send()
      }
    } else {
      if (enable && null != origin) {
        rctx.onResponse {
          it.header("Access-Control-Allow-Origin", origin)
          it.header("Vary", "origin")
        }
      }
      rctx.allowNext()
    }
  }
}