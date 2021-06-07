package vproxy.lib.common

import kotlinx.coroutines.*
import vproxy.base.connection.Connection
import vproxy.base.connection.NetEventLoop
import vproxy.base.connection.ServerSock
import vproxy.base.selector.SelectorEventLoop
import vproxy.base.selector.TimerEvent
import vproxy.base.util.Callback
import vproxy.base.util.promise.Promise
import vproxy.lib.tcp.CoroutineConnection
import vproxy.lib.tcp.CoroutineServerSock
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun SelectorEventLoop.dispatcher(): CoroutineDispatcher {
  return VProxyCoroutineExecutor(this).asCoroutineDispatcher()
}

class VProxyScheduledFuture(
  loop: SelectorEventLoop,
  private val delayMs: Int,
  command: Runnable,
) : ScheduledFuture<Any> {
  val completion = AtomicReference<Boolean?>()
  private val event: TimerEvent = loop.delay(delayMs) {
    if (completion.compareAndSet(null, true)) {
      command.run()
    }
  }

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    if (completion.compareAndSet(null, false)) {
      event.cancel()
      return true
    } else {
      return false
    }
  }

  override fun isCancelled(): Boolean {
    return completion.get() == false
  }

  override fun isDone(): Boolean {
    return completion.get() == true
  }

  override fun get(): Any? {
    return null
  }

  override fun get(timeout: Long, unit: TimeUnit): Any? {
    return null
  }

  override fun getDelay(unit: TimeUnit): Long {
    return unit.convert(Duration.ofMillis(delayMs.toLong()))
  }

  override fun compareTo(other: Delayed?): Int {
    var n = 0L
    if (other != null) {
      n = other.getDelay(TimeUnit.MILLISECONDS)
    }
    return (delayMs.toLong() - n).toInt()
  }
}

class VProxyCoroutineExecutor(
  private val loop: SelectorEventLoop,
) : AbstractExecutorService(), ScheduledExecutorService {
  override fun execute(command: Runnable) {
    loop.runOnLoop(command)
  }

  override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
    return VProxyScheduledFuture(loop, unit.toMillis(delay).toInt(), command)
  }

  override fun shutdown() {
    throw UnsupportedOperationException("should not be called")
  }

  override fun shutdownNow(): MutableList<Runnable> {
    throw UnsupportedOperationException("should not be called")
  }

  override fun isShutdown(): Boolean {
    throw UnsupportedOperationException("should not be called")
  }

  override fun isTerminated(): Boolean {
    throw UnsupportedOperationException("should not be called")
  }

  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
    throw UnsupportedOperationException("should not be called")
  }

  override fun <V : Any?> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
    throw UnsupportedOperationException("should not be called")
  }

  override fun scheduleAtFixedRate(
    command: Runnable,
    initialDelay: Long,
    period: Long,
    unit: TimeUnit
  ): ScheduledFuture<*> {
    throw UnsupportedOperationException("should not be called")
  }

  override fun scheduleWithFixedDelay(
    command: Runnable,
    initialDelay: Long,
    delay: Long,
    unit: TimeUnit
  ): ScheduledFuture<*> {
    throw UnsupportedOperationException("should not be called")
  }
}

suspend fun <T> Promise<T>.await(): T {
  return suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
    this.setHandler { res, err ->
      if (err != null) {
        cont.resumeWithException(err)
      } else {
        cont.resume(res)
      }
    }
  }
}

suspend fun <T, E : Exception> awaitCallback(f: (Callback<T, E>) -> Unit): T {
  return suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
    f(object : Callback<T, E>() {
      override fun onSucceeded(value: T) {
        cont.resume(value)
      }

      override fun onFailed(err: E) {
        cont.resumeWithException(err)
      }
    })
  }
}

suspend fun sleep(millis: Int) {
  return suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
    SelectorEventLoop.current().delay(millis) { cont.resume(Unit) }
  }
}

fun launchGlobal(exec: suspend CoroutineScope.() -> Unit) {
  val loop = SelectorEventLoop.current()
  if (loop == null) {
    throw IllegalStateException("currently not on any event loop: " + Thread.currentThread())
  }
  loop.launch(exec)
}

fun SelectorEventLoop.launch(exec: suspend CoroutineScope.() -> Unit) {
  GlobalScope.launch(this.dispatcher(), CoroutineStart.DEFAULT, exec)
}

fun Connection.fitCoroutine(loop: NetEventLoop): CoroutineConnection {
  return CoroutineConnection(loop, this)
}

fun ServerSock.fitCoroutine(loop: NetEventLoop): CoroutineServerSock {
  return CoroutineServerSock(loop, this)
}
