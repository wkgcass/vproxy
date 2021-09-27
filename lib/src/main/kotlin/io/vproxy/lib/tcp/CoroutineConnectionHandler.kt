package io.vproxy.lib.tcp

import io.vproxy.base.connection.ConnectionHandler
import io.vproxy.base.connection.ConnectionHandlerContext
import java.io.IOException

class CoroutineConnectionHandler : _root_ide_package_.io.vproxy.base.connection.ConnectionHandler {
  internal var willBeDetached = false
  internal var err: IOException? = null
  internal var eof: Boolean = false
  internal var readableEvent: ((err: IOException?) -> Unit)? = null
  internal var writableEvent: ((err: IOException?) -> Unit)? = null

  override fun readable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    val readableEvent = this.readableEvent
    if (readableEvent != null) {
      this.readableEvent = null
      readableEvent(null)
    }
  }

  override fun writable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    val writableEvent = this.writableEvent
    if (writableEvent != null) {
      this.writableEvent = null
      writableEvent(null)
    }
  }

  override fun exception(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?, err: IOException?) {
    val readableEvent = this.readableEvent
    val writableEvent = this.writableEvent
    this.readableEvent = null
    this.writableEvent = null

    this.err = err

    if (readableEvent != null) {
      readableEvent(err)
    }
    if (writableEvent != null) {
      writableEvent(err)
    }
  }

  override fun remoteClosed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    eof = true
    val readableEvent = this.readableEvent
    this.readableEvent = null
    if (readableEvent != null) {
      readableEvent(null)
    }
  }

  override fun closed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext?) {
    exception(ctx, IOException("closed"))
  }

  override fun removed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
    if (willBeDetached) {
      return
    }
    if (ctx.connection.isClosed && readableEvent == null && writableEvent == null) {
      return
    }
    exception(ctx, IOException("removed from event loop"))
  }
}
