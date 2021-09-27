package io.vproxy.lib.http.route

import io.vproxy.lib.http.RoutingContext

class WildcardSubPath(private val next: SubPath?) : SubPath {
  override fun next(): SubPath? {
    return next
  }

  override fun match(route: String): Boolean {
    return true
  }

  override fun currentSame(r: SubPath): Boolean {
    return r is WildcardSubPath
  }

  override fun fill(ctx: RoutingContext, route: String) {}

  override fun toString(): String {
    return "/*"
  }
}
