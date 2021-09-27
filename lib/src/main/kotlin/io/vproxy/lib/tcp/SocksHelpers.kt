package io.vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import io.vproxy.base.connection.ConnectableConnection
import io.vproxy.base.connection.ConnectableConnectionHandler
import io.vproxy.base.connection.ConnectableConnectionHandlerContext
import io.vproxy.base.connection.ConnectionHandlerContext
import io.vproxy.base.socks.Socks5ClientHandshake
import io.vproxy.base.util.callback.Callback
import vproxy.lib.common.__getCurrentNetEventLoopOrFail
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPPort
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun CoroutineConnection.socks5Proxy(targetHost: String, targetPort: Int) {
  val conn = this.conn
  if (conn !is _root_ide_package_.io.vproxy.base.connection.ConnectableConnection) {
    throw IllegalArgumentException("the connection is not a connectable connection")
  }

  val loop = __getCurrentNetEventLoopOrFail()
  this.detach()

  try {
    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      val handshake: _root_ide_package_.io.vproxy.base.socks.Socks5ClientHandshake
      val cb = object : _root_ide_package_.io.vproxy.base.util.callback.Callback<Void, IOException>() {
        override fun onSucceeded(value: Void?) {
          cont.resume(Unit)
        }

        override fun onFailed(err: IOException) {
          cont.resumeWithException(err)
        }
      }

      if (_root_ide_package_.io.vproxy.vfd.IP.isIpLiteral(targetHost)) {
        handshake = _root_ide_package_.io.vproxy.base.socks.Socks5ClientHandshake(
          conn,
          _root_ide_package_.io.vproxy.vfd.IPPort(targetHost, targetPort),
          cb
        )
      } else {
        handshake = _root_ide_package_.io.vproxy.base.socks.Socks5ClientHandshake(conn, targetHost, targetPort, cb)
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
  private val handshake: _root_ide_package_.io.vproxy.base.socks.Socks5ClientHandshake,
  private val failedEventFunc: (IOException) -> Unit,
) : _root_ide_package_.io.vproxy.base.connection.ConnectableConnectionHandler {
  override fun connected(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectableConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun readable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun writable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
    handshake.trigger()
  }

  override fun exception(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext, err: IOException) {
    failedEventFunc(err)
  }

  override fun remoteClosed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
    failedEventFunc(IOException("remote closed before socks5 handshaking done"))
  }

  override fun closed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
    failedEventFunc(IOException("closed before socks5 handshaking done"))
  }

  override fun removed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
    if (handshake.isDone) {
      return // handshake is done, no need to do anything
    }
    failedEventFunc(IOException("removed from event loop"))
  }
}
