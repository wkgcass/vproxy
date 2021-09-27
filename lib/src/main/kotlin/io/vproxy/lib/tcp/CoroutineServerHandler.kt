package io.vproxy.lib.tcp

import io.vproxy.base.connection.Connection
import io.vproxy.base.connection.ServerHandler
import io.vproxy.base.connection.ServerHandlerContext
import io.vproxy.base.util.RingBuffer
import vproxy.base.util.coll.Tuple
import io.vproxy.vfd.SocketFD
import java.io.IOException
import java.util.*

class CoroutineServerHandler(
  private val getIOBuffers: ((channel: _root_ide_package_.io.vproxy.vfd.SocketFD) -> Tuple<_root_ide_package_.io.vproxy.base.util.RingBuffer, _root_ide_package_.io.vproxy.base.util.RingBuffer>)?,
) : _root_ide_package_.io.vproxy.base.connection.ServerHandler {
  internal val acceptQ = LinkedList<_root_ide_package_.io.vproxy.base.connection.Connection>()
  internal var errQ = LinkedList<IOException>()
  internal var connectionEvent: ((IOException?, _root_ide_package_.io.vproxy.base.connection.Connection?) -> Unit)? = null

  override fun acceptFail(ctx: _root_ide_package_.io.vproxy.base.connection.ServerHandlerContext?, err: IOException?) {
    val connectionEvent = this.connectionEvent
    this.connectionEvent = null
    if (connectionEvent != null) {
      connectionEvent(err, null)
    } else {
      errQ.addLast(err)
    }
  }

  override fun connection(ctx: _root_ide_package_.io.vproxy.base.connection.ServerHandlerContext?, connection: _root_ide_package_.io.vproxy.base.connection.Connection?) {
    val connectionEvent = this.connectionEvent
    this.connectionEvent = null
    if (connectionEvent != null) {
      connectionEvent(null, connection)
    } else {
      acceptQ.addLast(connection)
    }
  }

  override fun getIOBuffers(channel: _root_ide_package_.io.vproxy.vfd.SocketFD): Tuple<_root_ide_package_.io.vproxy.base.util.RingBuffer, _root_ide_package_.io.vproxy.base.util.RingBuffer> {
    if (getIOBuffers != null) {
      return getIOBuffers(channel)
    }
    return Tuple(_root_ide_package_.io.vproxy.base.util.RingBuffer.allocateDirect(16384), _root_ide_package_.io.vproxy.base.util.RingBuffer.allocateDirect(16384))
  }

  override fun removed(ctx: _root_ide_package_.io.vproxy.base.connection.ServerHandlerContext) {
    if (ctx.server.isClosed) {
      return
    }
    acceptFail(ctx, IOException("removed from event loop"))
  }
}
