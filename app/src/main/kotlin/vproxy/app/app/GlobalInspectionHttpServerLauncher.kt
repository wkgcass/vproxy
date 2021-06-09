package vproxy.app.app

import kotlinx.coroutines.suspendCancellableCoroutine
import vproxy.base.GlobalInspection
import vproxy.base.connection.ServerSock
import vproxy.base.selector.SelectorEventLoop
import vproxy.lib.common.fitCoroutine
import vproxy.lib.common.launch
import vproxy.lib.http1.CoroutineHttp1Server
import vproxy.vfd.IPPort
import java.io.IOException
import kotlin.coroutines.resume

class GlobalInspectionHttpServerLauncher private constructor() {
  private var app: CoroutineHttp1Server? = null
  private var loop: SelectorEventLoop? = null

  @Throws(IOException::class)
  private fun launch0(l4addr: IPPort) {
    if (app != null) {
      throw IOException("GlobalInspectionHttpServer already started: $app")
    }

    val serverSock = ServerSock.create(l4addr)
    if (loop == null) {
      loop = SelectorEventLoop.open()
    }
    val app = CoroutineHttp1Server(serverSock.fitCoroutine(loop!!.ensureNetEventLoop()))

    app.get("/metrics") { it.conn.response(200).send(GlobalInspection.getInstance().prometheusString) }
    app.get("/lsof") {
      val data = suspendCancellableCoroutine<String> { cont ->
        GlobalInspection.getInstance().getOpenFDs { data -> cont.resume(data) }
      }
      it.conn.response(200).send(data)
    }
    app.get("/jstack") { it.conn.response(200).send(GlobalInspection.getInstance().stackTraces) }

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
    fun launch(l4addr: IPPort) {
      instance.launch0(l4addr)
    }

    @JvmStatic
    fun stop() {
      instance.stop0()
    }
  }
}