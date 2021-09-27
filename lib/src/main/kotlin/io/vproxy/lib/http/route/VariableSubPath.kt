package io.vproxy.lib.http.route

import vproxy.lib.http.RoutingContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class VariableSubPath(private val next: SubPath?, private val variable: String) : SubPath {
  override fun next(): SubPath? {
    return next
  }

  override fun match(route: String): Boolean {
    return true
  }

  override fun currentSame(r: SubPath): Boolean {
    return r is VariableSubPath && r.variable == variable
  }

  override fun fill(ctx: RoutingContext, route: String) {
    val res = try {
      URLDecoder.decode(route, StandardCharsets.UTF_8)
    } catch (ignore: RuntimeException) {
      route
    }
    ctx.putParam(variable, res)
  }

  override fun toString(): String {
    return "/:$variable"
  }
}
