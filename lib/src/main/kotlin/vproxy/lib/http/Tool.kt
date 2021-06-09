package vproxy.lib.http

import vjson.JSON
import vjson.JSON.parse
import vjson.cs.UTF8ByteArrayCharStream
import vjson.ex.JsonParseException
import vjson.util.ObjectBuilder

object Tool {
  @JvmStatic
  val bodyJson: StorageKey<JSON.Instance<*>> = object : StorageKey<JSON.Instance<*>> {}
  private val bodyJsonHandlerInstance = object : RoutingHandler {
    override suspend fun handle(rctx: RoutingContext) = handleBodyJson(rctx)
  }

  @JvmStatic
  fun bodyJsonHandler(): RoutingHandler {
    return bodyJsonHandlerInstance
  }

  private suspend fun handleBodyJson(ctx: RoutingContext) {
    val body = ctx.req.body()
    if (body.length() != 0) {
      val inst: JSON.Instance<*>
      try {
        inst = parse(UTF8ByteArrayCharStream(body.toJavaArray()))
      } catch (e: JsonParseException) {
        ctx.conn.response(400).send(
          ObjectBuilder()
            .put("status", 400)
            .put("reason", "Bad Request")
            .put("message", "request body is not valid json: " + e.message)
            .build()
        )
        return
      }
      ctx.put(bodyJson, inst)
    }
    ctx.allowNext()
    return
  }
}
