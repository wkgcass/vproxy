package io.vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineConnectingHandler(
  private val cont: CancellableContinuation<Unit>,
  private val resumeOnNextTick: Boolean,
) : io.vproxy.base.connection.ConnectableConnectionHandler {
  private var willBeDetached = false
  override fun connected(ctx: io.vproxy.base.connection.ConnectableConnectionHandlerContext) {
    willBeDetached = true
    ctx.eventLoop.removeConnection(ctx.connection)
    if (resumeOnNextTick) {
      // we may see canceled key exception later if only using one 'nextTick' call
      // nextTick { nextTick { ... } } ensures select() is called at least once
      ctx.eventLoop.selectorEventLoop.nextTick {
        ctx.eventLoop.selectorEventLoop.nextTick { cont.resume(Unit) }
      }
    } else {
      cont.resume(Unit)
    }
  }

  override fun readable(ctx: io.vproxy.base.connection.ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun writable(ctx: io.vproxy.base.connection.ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun exception(ctx: io.vproxy.base.connection.ConnectionHandlerContext?, err: IOException) {
    if (cont.isActive) {
      cont.resumeWithException(err)
    }
  }

  override fun remoteClosed(ctx: io.vproxy.base.connection.ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun closed(ctx: io.vproxy.base.connection.ConnectionHandlerContext?) {
    exception(ctx, IOException("closed"))
  }

  override fun removed(ctx: io.vproxy.base.connection.ConnectionHandlerContext?) {
    if (willBeDetached) {
      return
    }
    exception(ctx, IOException("removed from event loop"))
  }
}
