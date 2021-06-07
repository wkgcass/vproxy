package vproxy.lib.tcp

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import vproxy.base.connection.ConnectableConnection
import vproxy.base.connection.Connection
import vproxy.base.connection.NetEventLoop
import vproxy.base.util.ByteArray
import vproxy.base.util.RingBuffer
import vproxy.base.util.nio.ByteArrayChannel
import vproxy.lib.http1.CoroutineHttp1ClientConnection
import vproxy.lib.http1.CoroutineHttp1ServerConnection
import vproxy.vfd.IPPort
import java.io.EOFException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineConnection(
  private val loop: NetEventLoop,
  private val conn: Connection,
) {
  private var initialized = false
  private val handler = CoroutineConnectionHandler()

  private var reading = false
  private var writing = false

  suspend fun connect() {
    val conn: ConnectableConnection
    if (this.conn is ConnectableConnection) {
      conn = this.conn
    } else {
      return // already connected
    }
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      loop.addConnectableConnection(conn, null, CoroutineConnectingHandler(cont))
    }
  }

  private fun ensureInitialized() {
    if (initialized) {
      return
    }
    initialized = true
    loop.addConnection(conn, null, handler)
  }

  suspend fun read(): RingBuffer {
    if (reading) {
      throw IllegalStateException("another coroutine is reading from this connection")
    }
    handleExceptions()
    reading = true
    if (conn.inBuffer.used() != 0) {
      reading = false
      return conn.inBuffer
    }
    ensureInitialized()
    return suspendCancellableCoroutine { cont: CancellableContinuation<RingBuffer> ->
      handler.readableEvent = { err ->
        if (err != null) {
          reading = false
          cont.resumeWithException(err)
        } else {
          reading = false
          cont.resume(conn.inBuffer)
        }
      }
    }
  }

  suspend fun read(buf: ByteArray): Int {
    return read(buf, 0)
  }

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

  private fun handleExceptions() {
    val err = handler.err
    if (err != null) {
      throw err
    }
    if (handler.eof) {
      throw EOFException()
    }
    reading = true
  }

  suspend fun read(chnl: ByteArrayChannel) {
    if (reading) {
      throw IllegalStateException("another coroutine is reading from this connection")
    }
    handleExceptions()
    reading = true
    if (conn.inBuffer.used() > 0) {
      conn.inBuffer.writeTo(chnl)
      reading = false
      return
    }
    ensureInitialized()
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      handler.readableEvent = { err ->
        if (err != null) {
          reading = false
          cont.resumeWithException(err)
        } else {
          conn.inBuffer.writeTo(chnl)
          reading = false
          cont.resume(Unit)
        }
      }
    }
  }

  suspend fun write(buf: ByteArray) {
    if (writing) {
      throw IllegalStateException("another coroutine is writing to this connection")
    }
    val err = handler.err
    if (err != null) {
      throw err
    }
    writing = true
    if (conn.outBuffer.free() >= buf.length()) {
      conn.outBuffer.storeBytesFrom(ByteArrayChannel.from(buf, 0, buf.length(), 0))
      writing = false
      return
    }
    ensureInitialized()
    val chnl = ByteArrayChannel.from(buf, 0, buf.length(), 0)
    return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
      recursivelyWrite(cont, chnl)
    }
  }

  private fun recursivelyWrite(cont: CancellableContinuation<Unit>, chnl: ByteArrayChannel) {
    if (chnl.used() == 0) {
      writing = false
      cont.resume(Unit)
      return
    }
    handler.writableEvent = { err ->
      if (err != null) {
        writing = false
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

  fun close() {
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
}
