package io.vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import io.vproxy.base.connection.NetEventLoop
import io.vproxy.base.connection.ServerSock
import io.vproxy.base.util.RingBuffer
import vproxy.base.util.coll.Tuple
import io.vproxy.vfd.SocketFD
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineServerSock @JvmOverloads constructor(
  private val loop: _root_ide_package_.io.vproxy.base.connection.NetEventLoop,
  private val svr: _root_ide_package_.io.vproxy.base.connection.ServerSock,
  getIOBuffers: ((channel: _root_ide_package_.io.vproxy.vfd.SocketFD) -> Tuple<_root_ide_package_.io.vproxy.base.util.RingBuffer, _root_ide_package_.io.vproxy.base.util.RingBuffer>)? = null,
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

  suspend fun accept(): CoroutineConnection {
    if (!handler.errQ.isEmpty()) {
      throw handler.errQ.removeFirst()
    }
    if (!handler.acceptQ.isEmpty()) {
      val conn = handler.acceptQ.removeFirst()
      return CoroutineConnection(loop, conn)
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
