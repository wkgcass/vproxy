package io.vproxy.lib.http.route

import io.vproxy.lib.http.RoutingContext
import java.util.*
import java.util.stream.Collectors

interface SubPath {
  operator fun next(): SubPath?
  fun match(route: String): Boolean
  fun currentSame(r: SubPath): Boolean
  fun fill(ctx: RoutingContext, route: String)

  companion object {
    @JvmStatic
    fun create(path: String): SubPath? {
      val paths = Arrays.stream(path.split("/").toTypedArray()).map { obj: String -> obj.trim { it <= ' ' } }
        .filter { s: String -> !s.isEmpty() }.collect(Collectors.toList())
      var next: SubPath? = null
      for (i in paths.indices.reversed()) {
        val p = paths[i]
        next = if (p == "*") {
          WildcardSubPath(next)
        } else if (p.startsWith(":")) {
          VariableSubPath(next, p.substring(1))
        } else {
          FixedSubPath(next, p)
        }
      }
      return next
    }
  }
}
