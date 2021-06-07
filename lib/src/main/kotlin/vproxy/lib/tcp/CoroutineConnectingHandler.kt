package vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import vproxy.base.connection.ConnectableConnectionHandler
import vproxy.base.connection.ConnectableConnectionHandlerContext
import vproxy.base.connection.ConnectionHandlerContext
import java.io.IOException

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineConnectingHandler(
  private val cont: CancellableContinuation<Unit>,
) : ConnectableConnectionHandler {
  private var willBeRemoved = false
  override fun connected(ctx: ConnectableConnectionHandlerContext) {
    willBeRemoved = true
    ctx.eventLoop.removeConnection(ctx.connection)
    cont.resume(Unit)
  }

  override fun readable(ctx: ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun writable(ctx: ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun exception(ctx: ConnectionHandlerContext?, err: IOException) {
    if (cont.isActive) {
      cont.resumeWithException(err)
    }
  }

  override fun remoteClosed(ctx: ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun closed(ctx: ConnectionHandlerContext?) {
    exception(ctx, IOException("closed"))
  }

  override fun removed(ctx: ConnectionHandlerContext?) {
    if (willBeRemoved) {
      return
    }
    exception(ctx, IOException("removed from event loop"))
  }
}
