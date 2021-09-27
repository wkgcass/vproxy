package io.vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import io.vproxy.base.connection.ConnectableConnectionHandler
import io.vproxy.base.connection.ConnectableConnectionHandlerContext
import io.vproxy.base.connection.ConnectionHandlerContext
import java.io.IOException

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineConnectingHandler(
  private val cont: CancellableContinuation<Unit>,
) : _root_ide_package_.io.vproxy.base.connection.ConnectableConnectionHandler {
  private var willBeDetached = false
  override fun connected(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectableConnectionHandlerContext) {
    willBeDetached = true
    ctx.eventLoop.removeConnection(ctx.connection)
    cont.resume(Unit)
  }

  override fun readable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun writable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun exception(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?, err: IOException) {
    if (cont.isActive) {
      cont.resumeWithException(err)
    }
  }

  override fun remoteClosed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    throw UnsupportedOperationException("should not reach here")
  }

  override fun closed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    exception(ctx, IOException("closed"))
  }

  override fun removed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    if (willBeDetached) {
      return
    }
    exception(ctx, IOException("removed from event loop"))
  }
}
