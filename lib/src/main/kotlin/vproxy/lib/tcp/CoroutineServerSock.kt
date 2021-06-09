package vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import vproxy.base.connection.NetEventLoop
import vproxy.base.connection.ServerSock
import vproxy.base.util.RingBuffer
import vproxy.base.util.Tuple
import vproxy.vfd.SocketFD
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineServerSock @JvmOverloads constructor(
  private val loop: NetEventLoop,
  private val svr: ServerSock,
  getIOBuffers: ((channel: SocketFD) -> Tuple<RingBuffer, RingBuffer>)? = null,
) {
  private var initialized = false
  private val handler = CoroutineServerHandler(getIOBuffers)

  private fun ensureInitialized() {
    if (initialized) {
      return
    }
    initialized = true
    loop.addServer(svr, null, handler)
  }

  suspend fun accept(): CoroutineConnection {
    if (!handler.errQ.isEmpty()) {
      throw handler.errQ.removeFirst()
    }
    if (!handler.acceptQ.isEmpty()) {
      val conn = handler.acceptQ.removeFirst()
      return CoroutineConnection(loop, conn)
    }
    ensureInitialized()
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

  fun close() {
    svr.close()
  }

  override fun toString(): String {
    return "Coroutine$svr"
  }
}
