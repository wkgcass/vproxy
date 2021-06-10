package vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import vproxy.base.connection.ConnectableConnection
import vproxy.base.connection.Connection
import vproxy.base.connection.NetEventLoop
import vproxy.base.util.ByteArray
import vproxy.base.util.RingBuffer
import vproxy.base.util.nio.ByteArrayChannel
import vproxy.lib.common.vproxy
import vproxy.lib.http1.CoroutineHttp1ClientConnection
import vproxy.lib.http1.CoroutineHttp1ServerConnection
import vproxy.vfd.IPPort
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineConnection(
  private val loop: NetEventLoop,
  internal val conn: Connection,
) : AutoCloseable {
  private var handlerAdded = false
  private val handler = CoroutineConnectionHandler()

  private var reading = false
  private var writing = false
  private var detached = false
  private var connected = conn !is ConnectableConnection

  fun base(): Connection {
    return conn
  }

  suspend fun connect() {
    val conn: ConnectableConnection
    if (!connected && this.conn is ConnectableConnection) {
      conn = this.conn
    } else {
      return // already connected
    }
    suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      loop.addConnectableConnection(conn, null, CoroutineConnectingHandler(cont))
    }
    connected = true
  }

  private fun ensureHandler() {
    if (handlerAdded) {
      return
    }
    if (!connected) {
      throw IllegalStateException("not connected, cannot read nor write")
    }
    loop.addConnection(conn, null, handler)
    handlerAdded = true
  }

  private fun handleExceptions() {
    val err = handler.err
    if (err != null) {
      throw err
    }
  }

  /**
   * @return null when eof
   */
  suspend fun read(): RingBuffer? {
    if (reading) {
      throw IllegalStateException("another coroutine is reading from this connection")
    }
    handleExceptions()

    if (conn.inBuffer.used() != 0) {
      return conn.inBuffer
    }

    reading = true
    return vproxy.coroutine.run {
      defer { reading = false }

      ensureHandler()
      suspendCancellableCoroutine<Unit> { cont ->
        handler.readableEvent = { err ->
          if (err != null) {
            cont.resumeWithException(err)
          } else {
            cont.resume(Unit)
          }
        }
      }
      if (handler.eof && conn.inBuffer.used() == 0) {
        return@run null
      } else {
        return@run conn.inBuffer
      }
    }
  }

  /**
   * @return read bytes, 0 when eof
   */
  suspend fun read(buf: ByteArray): Int {
    return read(buf, 0)
  }

  /**
   * @return read bytes, 0 when eof
   */
  suspend fun read(buf: ByteArray, off: Int): Int {
    if (off == buf.length()) {
      return 0
    }
    val chnl = ByteArrayChannel.from(buf, off, off, buf.length() - off)
    val before = chnl.free()
    read(chnl)
    val after = chnl.free()
    return before - after
  }

  /**
   * @return read bytes, 0 when eof
   */
  suspend fun read(chnl: ByteArrayChannel): Int {
    if (reading) {
      throw IllegalStateException("another coroutine is reading from this connection")
    }
    handleExceptions()

    if (conn.inBuffer.used() > 0) {
      return conn.inBuffer.writeTo(chnl)
    }

    reading = true
    return vproxy.coroutine.run {
      defer { reading = false }

      ensureHandler()
      return@run suspendCancellableCoroutine { cont ->
        handler.readableEvent = { err ->
          if (err != null) {
            cont.resumeWithException(err)
          } else {
            cont.resume(conn.inBuffer.writeTo(chnl))
          }
        }
      }
    }
  }

  suspend fun write(str: String) {
    write(ByteArray.from(str))
  }

  suspend fun write(buf: ByteArray) {
    if (writing) {
      throw IllegalStateException("another coroutine is writing to this connection")
    }
    handleExceptions()

    writing = true
    vproxy.coroutine.run {
      defer { writing = false }

      if (conn.outBuffer.free() >= buf.length()) {
        conn.outBuffer.storeBytesFrom(ByteArrayChannel.from(buf, 0, buf.length(), 0))
        return@run
      }
      ensureHandler()
      val chnl = ByteArrayChannel.from(buf, 0, buf.length(), 0)
      return@run suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
        recursivelyWrite(cont, chnl)
      }
    }
  }

  private fun recursivelyWrite(cont: CancellableContinuation<Unit>, chnl: ByteArrayChannel) {
    if (chnl.used() == 0) {
      cont.resume(Unit)
      return
    }
    handler.writableEvent = { err ->
      if (err != null) {
        cont.resumeWithException(err)
      } else {
        conn.outBuffer.storeBytesFrom(chnl)
        recursivelyWrite(cont, chnl)
      }
    }
  }

  fun setTimeout(millis: Int) {
    conn.setTimeout(millis)
  }

  fun remote(): IPPort {
    return conn.remote
  }

  fun local(): IPPort {
    return conn.local
  }

  fun closeWrite() {
    conn.closeWrite()
  }

  override fun close() {
    conn.close()
  }

  override fun toString(): String {
    return "Coroutine:$conn"
  }

  fun asHttp1ClientConnection(): CoroutineHttp1ClientConnection {
    return CoroutineHttp1ClientConnection(this)
  }

  fun asHttp1ServerConnection(): CoroutineHttp1ServerConnection {
    return CoroutineHttp1ServerConnection(this)
  }

  internal fun detach() {
    if (reading) {
      throw IllegalStateException("another coroutine is reading from this connection")
    }
    if (writing) {
      throw IllegalStateException("another coroutine is writing to this connection")
    }
    if (detached) {
      throw IllegalStateException("the connection is already detached from event loop")
    }
    reading = true
    writing = true
    detached = true
    if (handlerAdded) {
      handler.willBeDetached = true
      loop.removeConnection(conn)
      handler.willBeDetached = false
    }
  }

  internal fun attach() {
    if (!detached) {
      throw IllegalStateException("the connection is not detached from event loop yet")
    }
    detached = false
    reading = false
    writing = false
    if (handlerAdded) {
      loop.addConnection(conn, null, handler)
    } else {
      ensureHandler()
    }
  }
}
