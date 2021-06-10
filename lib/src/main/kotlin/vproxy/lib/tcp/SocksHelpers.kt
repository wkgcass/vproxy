package vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import vproxy.base.connection.ConnectableConnection
import vproxy.base.connection.ConnectableConnectionHandler
import vproxy.base.connection.ConnectableConnectionHandlerContext
import vproxy.base.connection.ConnectionHandlerContext
import vproxy.base.socks.Socks5ClientHandshake
import vproxy.base.util.Callback
import vproxy.lib.common.__getCurrentNetEventLoopOrFail
import vproxy.vfd.IP
import vproxy.vfd.IPPort
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun CoroutineConnection.socks5Proxy(targetHost: String, targetPort: Int) {
  val conn = this.conn
  if (conn !is ConnectableConnection) {
    throw IllegalArgumentException("the connection is not a connectable connection")
  }

  val loop = __getCurrentNetEventLoopOrFail()
  this.detach()

  try {
    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      val handshake: Socks5ClientHandshake
      val cb = object : Callback<Void, IOException>() {
        override fun onSucceeded(value: Void?) {
          cont.resume(Unit)
        }

        override fun onFailed(err: IOException) {
          cont.resumeWithException(err)
        }
      }

      if (IP.isIpLiteral(targetHost)) {
        handshake = Socks5ClientHandshake(conn, IPPort(targetHost, targetPort), cb)
      } else {
        handshake = Socks5ClientHandshake(conn, targetHost, targetPort, cb)
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
  private val handshake: Socks5ClientHandshake,
  private val failedEventFunc: (IOException) -> Unit,
) : ConnectableConnectionHandler {
  override fun connected(ctx: ConnectableConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun readable(ctx: ConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun writable(ctx: ConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun exception(ctx: ConnectionHandlerContext, err: IOException) {
    failedEventFunc(err)
  }

  override fun remoteClosed(ctx: ConnectionHandlerContext) {
    failedEventFunc(IOException("remote closed before socks5 handshaking done"))
  }

  override fun closed(ctx: ConnectionHandlerContext) {
    failedEventFunc(IOException("closed before socks5 handshaking done"))
  }

  override fun removed(ctx: ConnectionHandlerContext) {
    if (handshake.isDone) {
      return // handshake is done, no need to do anything
    }
    failedEventFunc(IOException("removed from event loop"))
  }
}
