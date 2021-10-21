package io.vproxy.dep.vjson.util

import io.vproxy.dep.vjson.simple.SimpleString
import io.vproxy.dep.vjson.util.CastUtils.cast
import io.vproxy.dep.vjson.util.CastUtils.newAnyArray

class StringDictionary(val maxStorageStringLen: Int) {
  companion object {
    private val char2intMap = IntArray(129)
    private val int2charMap = IntArray(char2intMap.size)
    private val treeLeafOffset: Int

    init {
      var n = 0

      char2intMap['$'.code] = ++n
      int2charMap[n] = '$'.code

      char2intMap['-'.code] = ++n
      int2charMap[n] = '-'.code

      for (i in 0..9) {
        char2intMap[i + '0'.code] = ++n
        int2charMap[n] = i + '0'.code
      }

      for (i in 0..25) {
        char2intMap[i + 'A'.code] = ++n
        int2charMap[n] = i + 'A'.code
      }

      char2intMap['_'.code] = ++n
      int2charMap[n] = '_'.code

      for (i in 0..25) {
        char2intMap[i + 'a'.code] = ++n
        int2charMap[n] = i + 'a'.code
      }

      treeLeafOffset = n + 1
    }
  }

  private var storage = ArrayList<String>()
  private var tree = newAnyArray()

  inner class Traveler {
    private var text = TextBuilder(maxStorageStringLen)
    private var parentTree: Array<Any?>? = null
    private var currentTree: Array<Any?>? = null
    private var previousIndex = 0
    private var cannotHandle = false

    private fun reset() {
      text.clear()
      parentTree = null
      currentTree = null
      previousIndex = 0
      cannotHandle = false
    }

    fun next(c: Char) {
      // store into text buffer
      text.append(c)
      if (cannotHandle) {
        return
      }
      if (text.bufLen > maxStorageStringLen) {
        cannotHandle = true
        return
      }
      if (c.code >= char2intMap.size) {
        cannotHandle = true
        return
      }
      val cInt = char2intMap[c.code]
      if (cInt == 0) {
        cannotHandle = true
        return
      }

      // travel the trees
      var current = currentTree
      if (current == null) {
        current = tree
      }
      val nextArray: Array<Any?>
      // ensure have slot for current char
      if (current.size <= cInt + treeLeafOffset) {
        // need to expand
        val newCurrent = Array<Any?>(cInt + treeLeafOffset + 1) { null }
        for (i in current.indices) {
          newCurrent[i] = current[i]
        }
        current = newCurrent

        // add to parent
        val parent = parentTree
        if (parent == null) {
          tree = current
        } else {
          parent[previousIndex] = current
        }
        // make a dummy array for next
        nextArray = newAnyArray()
        current[cInt] = nextArray
      } else if (current[cInt] == null) {
        nextArray = newAnyArray()
        current[cInt] = nextArray
      } else {
        nextArray = cast(current[cInt])
      }
      // set parent/current/previousIndex
      parentTree = current
      currentTree = nextArray
      previousIndex = cInt
    }

    fun done(): String {
      val ret = record()
      reset()
      return ret
    }

    private fun record(): String {
      if (text.bufLen == 0) return "" // is empty
      if (cannotHandle) return text.toString() // cannot handle
      val parent = cast<Array<Any?>>(parentTree)
      // check and remove the dummy array
      if (parent[previousIndex] != null && cast<Array<Any?>>(parent[previousIndex]).isEmpty()) {
        parent[previousIndex] = null
      }
      // check whether string already exists
      if (parent[previousIndex + treeLeafOffset] != null) {
        val n = cast<Int>(parent[previousIndex + treeLeafOffset])
        return storage[n]
      }
      // otherwise found a new string, record it
      val str = text.toString()
      return handleConcurrentRecord(str, parent)
    }

    // #ifdef COVERAGE {{@lombok.Generated}}
    private fun handleConcurrentRecord(str: String, parent: Array<Any?>): String {
      // here we need to check whether the (str) is already recorded by another traveler
      if (storage.contains(str)) {
        val n = storage.indexOf(str)
        val ret = storage[n]
        parent[previousIndex + treeLeafOffset] = n // record the str in storage
        return ret
      }
      // not found in storage, add to it
      val n = storage.size
      storage.add(str)
      parent[previousIndex + treeLeafOffset] = n
      return str
    }

    override fun toString(): String = done()
  }

  fun traveler(): Traveler {
    return Traveler()
  }

  override fun toString(): String {
    val sb = StringBuilder()
    toString(sb, tree, "")
    return sb.toString()
  }

  private fun toString(sb: StringBuilder, tree: Array<Any?>, base: String) {
    var nonNullTreeSize = 0
    for (e in tree) {
      if (e == null) continue
      ++nonNullTreeSize
    }
    for (i in tree.indices) {
      val e = tree[i] ?: continue
      if (i < treeLeafOffset) {
        val charInfo = "" + int2charMap[i].toChar() + "/" + nonNullTreeSize + "/" + tree.size
        val nextBase = if (base == "") charInfo else "$base -> $charInfo"
        toString(sb, cast(e), nextBase)
      } else {
        val charInfo = int2charMap[i - treeLeafOffset].toChar() + "/" + nonNullTreeSize + "/" + tree.size
        sb.append(base).append(" -> ").append(charInfo).append(" -> ").append(
          SimpleString(storage.get(cast(e))).stringify()
        ).append("/").append(cast<Int>(e)).append("\n")
      }
    }
  }
}
