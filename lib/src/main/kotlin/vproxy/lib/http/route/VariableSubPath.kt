package vproxy.lib.http.route

import vproxy.lib.http.RoutingContext

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
    ctx.putParam(variable, route)
  }

  override fun toString(): String {
    return "/:$variable"
  }
}
