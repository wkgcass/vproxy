package io.vproxy.lib.tcp

import io.vproxy.lib.common.__getCurrentNetEventLoopOrFail
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun CoroutineConnection.socks5Proxy(targetHost: String, targetPort: Int) {
  val conn = this.conn
  if (conn !is io.vproxy.base.connection.ConnectableConnection) {
    throw IllegalArgumentException("the connection is not a connectable connection")
  }

  val loop = __getCurrentNetEventLoopOrFail()
  this.detach()

  try {
    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      val handshake: io.vproxy.base.socks.Socks5ClientHandshake
      val cb = object : io.vproxy.base.util.callback.Callback<Void, IOException>() {
        override fun onSucceeded(value: Void?) {
          cont.resume(Unit)
        }

        override fun onFailed(err: IOException) {
          cont.resumeWithException(err)
        }
      }

      if (io.vproxy.vfd.IP.isIpLiteral(targetHost)) {
        handshake = io.vproxy.base.socks.Socks5ClientHandshake(
          conn,
          io.vproxy.vfd.IPPort(targetHost, targetPort),
          cb
        )
      } else {
        handshake = io.vproxy.base.socks.Socks5ClientHandshake(conn, targetHost, targetPort, cb)
      }
      loop.addConnectableConnection(conn, null,
        Socks5ClientConnectableConnectionHandler(handshake) { cont.resumeWithException(it) }
      )
    }
  } finally {
    loop.removeConnection(conn)
    this.attach()
  }
}

private class Socks5ClientConnectableConnectionHandler constructor(
  private val handshake: io.vproxy.base.socks.Socks5ClientHandshake,
  private val failedEventFunc: (IOException) -> Unit,
) : io.vproxy.base.connection.ConnectableConnectionHandler {
  override fun connected(ctx: io.vproxy.base.connection.ConnectableConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun readable(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun writable(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun exception(ctx: io.vproxy.base.connection.ConnectionHandlerContext, err: IOException) {
    failedEventFunc(err)
  }

  override fun remoteClosed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
    failedEventFunc(IOException("remote closed before socks5 handshaking done"))
  }

  override fun closed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
    failedEventFunc(IOException("closed before socks5 handshaking done"))
  }

  override fun removed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
    if (handshake.isDone) {
      return // handshake is done, no need to do anything
    }
    failedEventFunc(IOException("removed from event loop"))
  }
}
