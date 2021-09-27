package vproxy.lib.common

import vjson.CharStream
import vproxy.base.util.ByteArray
import java.nio.charset.Charset

class ByteArrayCharStream(data: ByteArray, charset: Charset) : CharStream {
  private val chars: CharArray = String(data.toJavaArray(), charset).toCharArray()
  private var idx = -1
  override fun hasNext(i: Int): Boolean {
    return idx + i < chars.size
  }

  override fun moveNextAndGet(): Char {
    return chars[++idx]
  }

  override fun peekNext(i: Int): Char {
    return chars[idx + i]
  }
}
