package io.vproxy.poc

import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.launch
import io.vproxy.lib.http1.CoroutineHttp1Server
import java.io.File
import java.io.FileOutputStream

object SwitchTCP {
  @Throws(Exception::class)
  @JvmStatic
  fun main(args: Array<String>) {
    val elg = io.vproxy.base.component.elgroup.EventLoopGroup("elg0")
    val sw = io.vproxy.vswitch.Switch(
      "sw0",
      io.vproxy.vfd.IPPort("127.0.0.1", 18472),
      elg,
      60000,
      60000,
      io.vproxy.component.secure.SecurityGroup.allowAll(),
      1500,
      true
    )
    sw.start()
    elg.add("el0")
    val el = elg["el0"]
    val loop = el.selectorEventLoop
    val script = """
        sudo ifconfig tap1 172.16.3.55/24
        sudo ifconfig tap1 inet6 add fd00::337/120
        
        """.trimIndent()
    val f = File.createTempFile("tap1", ".sh")
    f.deleteOnExit()
    FileOutputStream(f).use { fos ->
      fos.write(script.toByteArray())
      fos.flush()
    }
    f.setExecutable(true)
    sw.addNetwork(3,
      io.vproxy.base.util.Network("172.16.3.0/24"),
      io.vproxy.base.util.Network("[fd00::300]/120"), null)
    sw.addTap("tap1", 3, f.absolutePath)
    val network = sw.getNetwork(3)
    network.addIp(
      io.vproxy.vfd.IP.from("172.16.3.254"),
      io.vproxy.vfd.MacAddress("00:00:00:00:03:04"), null)
    val fds = network.fds()
    val serverSock = io.vproxy.base.connection.ServerSock.create(io.vproxy.vfd.IPPort("0.0.0.0", 80), fds)

    val httpServer = CoroutineHttp1Server(serverSock.coroutine(el))
    httpServer.get("/hello") { it.conn.response(200).send("world\r\n") }
    val largeBuffer = io.vproxy.base.util.ByteArray.allocate(1024 * 1024)
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toByteArray()
    for (i in 0 until largeBuffer.length()) {
      largeBuffer[i] = chars[i % chars.size]
    }
    httpServer.get("/large") { it.conn.response(200).send(largeBuffer) }
    httpServer.post("/validate") {
      val body = it.req.body()
      if (body.length() == 0) {
        it.conn.response(400).send("body not provided\r\n")
        return@post
      }
      if (body.length() != 1024 * 1024) {
        it.conn.response(400).send("body length is not 1024 * 1024\r\n")
        return@post
      }
      for (i in 0 until 1024 * 1024) {
        if (body.get(i) != chars[i % chars.size]) {
          it.conn.response(400).send(
            "invalid char at index $i, expecting ${chars[i % chars.size].toChar()}, but got ${body.get(i).toChar()}"
          )
          return@post
        }
      }
      it.conn.response(200).send("OK\r\n")
    }
    loop.launch {
      httpServer.start()
    }
    val serverSock88: io.vproxy.base.connection.ServerSock = io.vproxy.base.connection.ServerSock.create(
      io.vproxy.vfd.IPPort("0.0.0.0", 88), fds)
    el.addServer(serverSock88, null, io.vproxy.test.tool.CommandServer())
  }
}
