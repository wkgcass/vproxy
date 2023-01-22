/*
 * The MIT License
 *
 * Copyright 2021 wkgcass (https://github.com/wkgcass)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.vproxy.dep.vjson.cs

import io.vproxy.dep.vjson.CharStream
import io.vproxy.dep.vjson.ex.ParserException
import io.vproxy.dep.vjson.parser.ParserOptions
import io.vproxy.dep.vjson.parser.StringParser
import io.vproxy.dep.vjson.util.Manager
import io.vproxy.dep.vjson.util.collection.VList

class IncludeCharStream(private val csMap: Manager<String>, mainCSName: String) : CharStream {
  companion object {
    private val includeStr = "#include".toCharArray()
  }

  private val csMapCache = HashMap<String, String>()
  private var current = formatCS(mainCSName, null)
  private var actualCurrent = current

  private val peekChars = VList<PeekChar>()

  private class StackedCS(
    val name: String,
    val cs: PeekCharStream,
    var lastRetrievedOffset: Int,
    val parent: StackedCS?,
  ) {

    fun hasNext(i: Int = 1): Boolean = cs.hasNext(i)
    fun peekNext(i: Int): Char = cs.peekNext(i)
    fun lineCol(): LineCol = cs.lineCol()

    fun moveNextAndGet(): Char = cs.moveNextAndGet()
    fun moveNextAndGetAndMoveOffset(): Char {
      val c = cs.moveNextAndGet()
      lastRetrievedOffset += 1
      return c
    }

    fun skipBlank(skipComments: Boolean) {
      cs.skipBlank(skipComments)
      lastRetrievedOffset = cs.getCursor()
    }

    fun skip(n: Int) {
      cs.skip(n)
      lastRetrievedOffset += n
    }
  }

  private data class PeekChar(val c: Char, val cs: StackedCS)

  private fun getContent(name: String): String {
    var cacheStr = csMapCache[name]
    if (cacheStr == null) {
      val f = csMap.provide(name)!!
      cacheStr = f()
      csMapCache[name] = cacheStr
    }
    return cacheStr
  }

  private fun formatCS(name: String, parent: StackedCS?): StackedCS {
    return StackedCS(name, PeekCharStream(LineColCharStream(CharStream.from(getContent(name)), name)), 0, parent)
  }

  private fun checkStackAndSkipBlank() {
    if (current.parent == null) return
    current = current.parent!!
    currentSkipBlank()
  }

  private fun checkCurrentAndSkipBlank() {
    current.skipBlank(true)
    if (!current.hasNext()) {
      checkStackAndSkipBlank()
    }
  }

  private fun checkRecursiveInclude(name: String, lineCol: LineCol) {
    var cs: StackedCS? = current
    while (cs != null) {
      if (cs.name == name)
        throw ParserException("recursive include: $name", lineCol)
      cs = cs.parent
    }
  }

  private fun currentSkipBlank() {
    current.skipBlank(false)
    if (!current.hasNext()) {
      checkStackAndSkipBlank()
      return
    }
    val includeLineCol: LineCol = current.lineCol()
    for (i in 1..includeStr.size) {
      if (!current.hasNext(i)) {
        return checkCurrentAndSkipBlank()
      }
      val c = current.peekNext(i)
      if (c != includeStr[i - 1]) {
        return checkCurrentAndSkipBlank()
      }
    }
    if (!current.hasNext(includeStr.size + 1)) {
      throw ParserException("invalid #include statement: reaches eof", includeLineCol)
    }
    val parser = StringParser(ParserOptions().setEnd(false))
    val pcs = PeekCharStream(current.cs, includeStr.size)
    val includeNameObj = try {
      parser.feed(pcs)
    } catch (e: ParserException) {
      throw ParserException("invalid #include statement: " + e.message, includeLineCol)
    } ?: throw ParserException("invalid #include statement: missing char stream name to be included", includeLineCol)
    current.skip(pcs.getCursor())

    val includeName = includeNameObj.toJavaObject()
    csMap.provide(includeName)
      ?: throw ParserException("unable to #include ${includeNameObj.stringify()}: char stream not found", includeLineCol)
    checkRecursiveInclude(includeName, includeLineCol)

    current = formatCS(includeName, current)
    currentSkipBlank()
  }

  override fun skipBlank(skipComments: Boolean) {
    current = actualCurrent
    current.cs.setCursor(current.lastRetrievedOffset)
    peekChars.clear()

    if (skipComments) {
      currentSkipBlank()
    } else {
      while (true) {
        current.skipBlank(false)
        if (current.hasNext()) break
        else {
          if (current.parent == null) break
          current = current.parent!!
        }
      }
    }
    actualCurrent = current
  }

  override fun hasNext(i: Int): Boolean {
    if (peekChars.size() >= i) return true
    while (peekChars.size() < i) {
      if (!current.hasNext()) {
        return false
      }
      peekNext(i)
    }
    return true
  }

  private fun currentMoveNextAndGet(isPeek: Boolean): Char {
    while (true) {
      if (current.hasNext()) {
        return if (isPeek) current.moveNextAndGet() else current.moveNextAndGetAndMoveOffset()
      }
      if (current.parent == null) throw IndexOutOfBoundsException()
      else {
        current = current.parent!!
        if (!isPeek) {
          actualCurrent = current
        }
      }
    }
  }

  override fun moveNextAndGet(): Char {
    if (!peekChars.isEmpty()) {
      val pc = peekChars.removeFirst()
      pc.cs.lastRetrievedOffset += 1
      actualCurrent = pc.cs
      return pc.c
    }
    return currentMoveNextAndGet(false)
  }

  override fun peekNext(i: Int): Char {
    if (peekChars.size() >= i) return peekChars.get(i - 1).c
    while (peekChars.size() < i) {
      val c = currentMoveNextAndGet(true)
      peekChars.add(PeekChar(c, current))
    }
    return peekChars.last().c
  }

  override fun lineCol(): LineCol = current.lineCol()
}
