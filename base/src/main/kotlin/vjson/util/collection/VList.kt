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
package vjson.util.collection

class VList<E> {
  private var head: Node<E>? = null
  private var tail: Node<E>? = null
  private var size: Int = 0

  fun size(): Int {
    return size
  }

  fun isEmpty(): Boolean {
    return size() == 0
  }

  fun add(index: Int, e: E) {
    if (index < 0) {
      throw IndexOutOfBoundsException("index = $index < 0")
    } else if (index > size()) {
      throw IndexOutOfBoundsException("index = $index > size = $size")
    }
    if (index == 0) {
      addFirst(e)
      return
    } else if (index == size()) {
      add(e)
      return
    }

    var n = head!!
    for (i in 0 until (index - 1)) {
      n = n.next!!
    }
    Node(n, e)
    ++size
  }

  fun add(e: E) {
    val n = Node(tail, e)
    if (tail == null) {
      head = n
    }
    tail = n
    ++size
  }

  fun addFirst(e: E) {
    val n = Node(null, e)
    val head = this.head
    if (head == null) {
      tail = n
    } else {
      n.next = head
      head.prev = n
    }
    this.head = n
    ++size
  }

  fun first(): E {
    val head = this.head ?: throw NoSuchElementException()
    return head.element
  }

  fun last(): E {
    val tail = this.tail ?: throw NoSuchElementException()
    return tail.element
  }

  fun get(index: Int): E {
    if (index < 0) {
      throw IndexOutOfBoundsException("index = $index < 0")
    } else if (index >= size()) {
      throw IndexOutOfBoundsException("index = $index >= size = $size")
    }
    var n = head!!
    for (i in 0 until index) {
      n = n.next!!
    }
    return n.element
  }

  @Suppress("DuplicatedCode")
  fun removeLast(): E {
    val removed = this.tail ?: throw NoSuchElementException()
    val prev = removed.prev
    tail = prev
    if (prev == null) {
      head = null
    } else {
      prev.next = null
    }
    size -= 1
    return removed.element
  }

  @Suppress("DuplicatedCode")
  fun removeFirst(): E {
    val removed = this.head ?: throw NoSuchElementException()
    val next = removed.next
    head = next
    if (next == null) {
      tail = null
    } else {
      next.prev = null
    }
    size -= 1
    return removed.element
  }

  private class Node<E>(prev: Node<E>?, val element: E) {
    @Suppress("CanBePrimaryConstructorProperty")
    var prev: Node<E>? = prev
    var next: Node<E>? = null

    init {
      if (prev != null) {
        val next = prev.next
        if (next != null) {
          this.next = next
          next.prev = this
        }
        prev.next = this
      }
    }
  }
}
