package io.vproxy.lib.http.route

import vproxy.lib.http.RoutingContext

class FixedSubPath(private val next: SubPath?, private val route: String) : SubPath {
  override fun next(): SubPath? {
    return next
  }

  override fun match(route: String): Boolean {
    return this.route == route
  }

  override fun currentSame(r: SubPath): Boolean {
    return r is FixedSubPath && r.route == route
  }

  override fun fill(ctx: RoutingContext, route: String) {}

  override fun toString(): String {
    return "/$route"
  }
}
