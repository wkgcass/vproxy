package io.vproxy.lib.http1

import io.vproxy.lib.tcp.CoroutineConnection

abstract class CoroutineHttp1Common(private val conn: CoroutineConnection) {
  private var headersSent = false

  protected abstract suspend fun sendHeadersBeforeChunks()

  open suspend fun sendChunk(payload: io.vproxy.base.util.ByteArray): CoroutineHttp1Common {
    if (!headersSent) {
      headersSent = true
      sendHeadersBeforeChunks()
    }
    val chunk = io.vproxy.base.processor.http1.entity.Chunk()
    chunk.size = payload.length()
    chunk.content = payload
    conn.write(chunk.toByteArray())

    return this
  }

  open suspend fun endChunks(trailers: List<io.vproxy.base.processor.http1.entity.Header>) {
    val chunk = io.vproxy.base.processor.http1.entity.Chunk()
    chunk.size = 0

    val textPart = StringBuilder()
    for (h in trailers) {
      textPart.append(h.key).append(": ").append(h.value).append("\r\n")
    }
    textPart.append("\r\n") // end req

    conn.write(
      chunk.toByteArray()
        .concat(io.vproxy.base.util.ByteArray.from(textPart.toString()))
    )
  }
}
