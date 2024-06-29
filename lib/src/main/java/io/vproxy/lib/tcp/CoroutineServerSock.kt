package io.vproxy.lib.tcp

import io.vproxy.base.util.coll.Tuple
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineServerSock @JvmOverloads constructor(
  private val loop: io.vproxy.base.connection.NetEventLoop,
  val svr: io.vproxy.base.connection.ServerSock,
  getIOBuffers: ((channel: io.vproxy.vfd.SocketFD) -> Tuple<io.vproxy.base.util.RingBuffer, io.vproxy.base.util.RingBuffer>)? = null,
): AutoCloseable {
  private var handlerAdded = false
  private val handler = CoroutineServerHandler(getIOBuffers)

  private fun ensureHandler() {
    if (handlerAdded) {
      return
    }
    handlerAdded = true
    loop.addServer(svr, null, handler)
  }

  suspend fun accept(): CoroutineConnection? {
    if (!handler.errQ.isEmpty()) {
      throw handler.errQ.removeFirst()
    }
    if (!handler.acceptQ.isEmpty()) {
      val conn = handler.acceptQ.removeFirst()
      return CoroutineConnection(loop, conn)
    }
    if (svr.isClosed) {
      return null
    }
    ensureHandler()
    return suspendCancellableCoroutine { cont: CancellableContinuation<CoroutineConnection> ->
      handler.connectionEvent = { err, conn ->
        if (err != null) {
          cont.resumeWithException(err)
        } else {
          cont.resume(CoroutineConnection(loop, conn!!))
        }
      }
    }
  }

  override fun close() {
    svr.close()
  }

  override fun toString(): String {
    return "Coroutine$svr"
  }
}
