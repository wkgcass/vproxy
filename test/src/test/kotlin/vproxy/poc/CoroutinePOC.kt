package vproxy.poc

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import vproxy.base.selector.SelectorEventLoop
import vproxy.base.selector.coroutine.delay
import vproxy.base.selector.coroutine.dispatcher
import vproxy.base.util.thread.VProxyThread

@Suppress("BlockingMethodInNonBlockingContext")
object CoroutinePOC {
  @JvmStatic
  fun main(args: Array<String>) {
    val loop = SelectorEventLoop.open()
    loop.loop { VProxyThread.create(it, "coroutine-poc") }
    GlobalScope.launch(loop.dispatcher()) {
      println("wait for 1 sec on thread: " + Thread.currentThread())
      delay(1000)
      println("hello coroutine on thread: " + Thread.currentThread())

      loop.close()
    }
  }
}
