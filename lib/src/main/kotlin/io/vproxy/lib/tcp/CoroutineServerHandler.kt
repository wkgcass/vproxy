package io.vproxy.lib.tcp

import io.vproxy.base.util.coll.Tuple
import java.io.IOException
import java.util.*

class CoroutineServerHandler(
  private val getIOBuffers: ((channel: io.vproxy.vfd.SocketFD) -> Tuple<io.vproxy.base.util.RingBuffer, io.vproxy.base.util.RingBuffer>)?,
) : io.vproxy.base.connection.ServerHandler {
  internal val acceptQ = LinkedList<io.vproxy.base.connection.Connection>()
  internal var errQ = LinkedList<IOException>()
  internal var connectionEvent: ((IOException?, io.vproxy.base.connection.Connection?) -> Unit)? = null

  override fun acceptFail(ctx: io.vproxy.base.connection.ServerHandlerContext?, err: IOException?) {
    val connectionEvent = this.connectionEvent
    this.connectionEvent = null
    if (connectionEvent != null) {
      connectionEvent(err, null)
    } else {
      errQ.addLast(err)
    }
  }

  override fun connection(ctx: io.vproxy.base.connection.ServerHandlerContext?, connection: io.vproxy.base.connection.Connection?) {
    val connectionEvent = this.connectionEvent
    this.connectionEvent = null
    if (connectionEvent != null) {
      connectionEvent(null, connection)
    } else {
      acceptQ.addLast(connection)
    }
  }

  override fun getIOBuffers(channel: io.vproxy.vfd.SocketFD): Tuple<io.vproxy.base.util.RingBuffer, io.vproxy.base.util.RingBuffer> {
    if (getIOBuffers != null) {
      return getIOBuffers(channel)
    }
    return Tuple(io.vproxy.base.util.RingBuffer.allocateDirect(16384), io.vproxy.base.util.RingBuffer.allocateDirect(16384))
  }

  override fun removed(ctx: io.vproxy.base.connection.ServerHandlerContext) {
    if (ctx.server.isClosed) {
      return
    }
    acceptFail(ctx, IOException("removed from event loop"))
  }
}
