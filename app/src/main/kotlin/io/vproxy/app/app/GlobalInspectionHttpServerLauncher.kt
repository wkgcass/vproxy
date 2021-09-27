package io.vproxy.app.app

import kotlinx.coroutines.suspendCancellableCoroutine
import io.vproxy.base.GlobalInspection
import io.vproxy.base.connection.ServerSock
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.coroutine
import vproxy.lib.common.launch
import vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vfd.IPPort
import java.io.IOException
import kotlin.coroutines.resume

class GlobalInspectionHttpServerLauncher private constructor() {
  private var app: CoroutineHttp1Server? = null
  private var loop: _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop? = null

  private fun launch0(l4addr: _root_ide_package_.io.vproxy.vfd.IPPort) {
    if (app != null) {
      throw IOException("GlobalInspectionHttpServer already started: $app")
    }

    val serverSock = _root_ide_package_.io.vproxy.base.connection.ServerSock.create(l4addr)
    if (loop == null) {
      loop = _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.open()
      loop!!.loop { _root_ide_package_.io.vproxy.base.util.thread.VProxyThread.create(it, "global-inspection") }
    }
    val app = CoroutineHttp1Server(serverSock.coroutine(loop!!.ensureNetEventLoop()))

    app.get("/metrics") { it.conn.response(200).send(_root_ide_package_.io.vproxy.base.GlobalInspection.getInstance().prometheusString) }
    app.get("/lsof") {
      val data = suspendCancellableCoroutine<String> { cont ->
        _root_ide_package_.io.vproxy.base.GlobalInspection.getInstance().getOpenFDs { data -> cont.resume(data) }
      }
      it.conn.response(200).send(data)
    }
    app.get("/lsof/tree") {
      val data = suspendCancellableCoroutine<String> { cont ->
        _root_ide_package_.io.vproxy.base.GlobalInspection.getInstance().getOpenFDsTree { data -> cont.resume(data) }
      }
      it.conn.response(200).send(data)
    }
    app.get("/jstack") { it.conn.response(200).send(_root_ide_package_.io.vproxy.base.GlobalInspection.getInstance().stackTraces) }

    loop!!.launch {
      app.start()
    }
  }

  private fun stop0() {
    if (app != null) {
      app!!.close()
      app = null
    }
    if (loop != null) {
      loop!!.close()
      loop = null
    }
  }

  companion object {
    private val instance = GlobalInspectionHttpServerLauncher()

    @Throws(IOException::class)
    @JvmStatic
    fun launch(l4addr: _root_ide_package_.io.vproxy.vfd.IPPort) {
      instance.launch0(l4addr)
    }

    @JvmStatic
    fun stop() {
      instance.stop0()
    }
  }
}
