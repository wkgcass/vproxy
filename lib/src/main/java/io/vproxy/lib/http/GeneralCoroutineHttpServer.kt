package io.vproxy.lib.http

import io.vproxy.lib.http.route.SubPath

abstract class GeneralCoroutineHttpServer<CoroutineHttpServer : GeneralCoroutineHttpServer<CoroutineHttpServer>> {
  protected var started = false

  protected val routes: Map<HttpMethod, io.vproxy.base.util.coll.Tree<SubPath, RoutingHandler>> =
    object : HashMap<HttpMethod, io.vproxy.base.util.coll.Tree<SubPath, RoutingHandler>>(HttpMethod.values().size) {
      init {
        for (m in HttpMethod.values()) {
          put(m, io.vproxy.base.util.coll.Tree())
        }
      }
    }

  fun get(route: String, handler: RoutingHandlerFunc): CoroutineHttpServer {
    return handle(HttpMethod.GET, route, handler)
  }

  fun get(route: String, handler: RoutingHandler): CoroutineHttpServer {
    return handle(HttpMethod.GET, route, handler)
  }

  fun post(route: String, handler: RoutingHandlerFunc): CoroutineHttpServer {
    return handle(HttpMethod.POST, route, handler)
  }

  fun post(route: String, handler: RoutingHandler): CoroutineHttpServer {
    return handle(HttpMethod.POST, route, handler)
  }

  fun put(route: String, handler: RoutingHandlerFunc): CoroutineHttpServer {
    return handle(HttpMethod.PUT, route, handler)
  }

  fun put(route: String, handler: RoutingHandler): CoroutineHttpServer {
    return handle(HttpMethod.PUT, route, handler)
  }

  fun del(route: String, handler: RoutingHandlerFunc): CoroutineHttpServer {
    return handle(HttpMethod.DELETE, route, handler)
  }

  fun del(route: String, handler: RoutingHandler): CoroutineHttpServer {
    return handle(HttpMethod.DELETE, route, handler)
  }

  fun all(route: String, handler: RoutingHandlerFunc): CoroutineHttpServer {
    return handle(HttpMethod.ALL_METHODS, SubPath.create(route), object : RoutingHandler {
      override suspend fun handle(rctx: RoutingContext) = handler(rctx)
    })
  }

  fun all(route: String, handler: RoutingHandler): CoroutineHttpServer {
    return handle(HttpMethod.ALL_METHODS, SubPath.create(route), handler)
  }

  private fun handle(method: HttpMethod, route: String, handler: RoutingHandlerFunc): CoroutineHttpServer {
    return handle(method, route, object : RoutingHandler {
      override suspend fun handle(rctx: RoutingContext) = handler(rctx)
    })
  }

  fun handle(method: HttpMethod, route: String, handler: RoutingHandler): CoroutineHttpServer {
    return handle(method, SubPath.create(route), handler)
  }

  fun handle(method: HttpMethod, route: SubPath?, handler: RoutingHandler): CoroutineHttpServer {
    return handle(arrayOf(method), route, handler)
  }

  @Suppress("unchecked_cast")
  fun handle(methods: Array<HttpMethod>, route: SubPath?, handler: RoutingHandler): CoroutineHttpServer {
    check(!started) { "This http server is already started" }
    record(methods, route, handler)
    return this as CoroutineHttpServer
  }

  private fun record(tree: io.vproxy.base.util.coll.Tree<SubPath, RoutingHandler>, subpath: SubPath?, handler: RoutingHandler) {
    // null means this sub-path is '/' which is the end of this route
    if (subpath == null) {
      tree.leaf(handler)
      return
    }
    // check the last branch
    // note: here we only check the LAST branch because we need to preserve the handling order
    // consider this situation:
    // 1. handle(..., /a/b,  ...) registers /a/b
    // 2. handle(..., /a/:x, ...) registers /a/:x
    // 3. handle(..., /a/b,  ...) registers /a/b
    // we must NOT add the 3rd path to the branch of the 1st path because if so we will get order 1st,3rd,2nd
    // instead of the expected order 1st,2nd,3rd
    val last = tree.lastBranch()
    if (last != null && last.data.currentSame(subpath)) {
      // can use the last node
      record(last, subpath.next(), handler)
      return
    }
    // must be new route
    val br = tree.branch(subpath)
    record(br, subpath.next(), handler)
  }

  private fun record(methods: Array<HttpMethod>, route: SubPath?, handler: RoutingHandler) {
    for (m in methods) {
      record(routes[m]!!, route, handler)
    }
  }
}
