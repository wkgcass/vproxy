package vproxy.lib.tcp

import vproxy.base.connection.Connection
import vproxy.base.connection.ServerHandler
import vproxy.base.connection.ServerHandlerContext
import vproxy.base.util.RingBuffer
import vproxy.base.util.coll.Tuple
import vproxy.vfd.SocketFD
import java.io.IOException
import java.util.*

class CoroutineServerHandler(
  private val getIOBuffers: ((channel: SocketFD) -> Tuple<RingBuffer, RingBuffer>)?,
) : ServerHandler {
  internal val acceptQ = LinkedList<Connection>()
  internal var errQ = LinkedList<IOException>()
  internal var connectionEvent: ((IOException?, Connection?) -> Unit)? = null

  override fun acceptFail(ctx: ServerHandlerContext?, err: IOException?) {
    val connectionEvent = this.connectionEvent
    this.connectionEvent = null
    if (connectionEvent != null) {
      connectionEvent(err, null)
    } else {
      errQ.addLast(err)
    }
  }

  override fun connection(ctx: ServerHandlerContext?, connection: Connection?) {
    val connectionEvent = this.connectionEvent
    this.connectionEvent = null
    if (connectionEvent != null) {
      connectionEvent(null, connection)
    } else {
      acceptQ.addLast(connection)
    }
  }

  override fun getIOBuffers(channel: SocketFD): Tuple<RingBuffer, RingBuffer> {
    if (getIOBuffers != null) {
      return getIOBuffers(channel)
    }
    return Tuple(RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384))
  }

  override fun removed(ctx: ServerHandlerContext) {
    if (ctx.server.isClosed) {
      return
    }
    acceptFail(ctx, IOException("removed from event loop"))
  }
}
