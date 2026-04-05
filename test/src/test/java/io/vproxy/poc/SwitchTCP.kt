package io.vproxy.poc

import io.vproxy.base.component.elgroup.EventLoopGroup
import io.vproxy.base.connection.ServerSock
import io.vproxy.base.util.ByteArray
import io.vproxy.base.util.Network
import io.vproxy.base.util.OS
import io.vproxy.base.util.Utils
import io.vproxy.component.secure.SecurityGroup
import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.launch
import io.vproxy.lib.common.sleep
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPPort
import io.vproxy.vfd.MacAddress
import io.vproxy.vswitch.Switch
import java.io.File
import java.io.FileOutputStream
import java.net.NetworkInterface
import kotlin.system.exitProcess

/**
 * 在 vSwitch 上通过 TUN 设备搭建 HTTP 服务的 POC。
 *
 * 启动方式（需要root权限）：
 *   ./gradlew SwitchTCP
 *
 * 测试方式：
 *   1. 将SwitchTCP运行于后台：
 *     ./gradlew SwitchTCP &
 *     可选地，可以设置自动退出时间，例如 curl -X POST $host/exit --data '30'
 *   2. 使用curl访问 /large 接口，输出到/tmp/large中
 *     rm -f /tmp/large && curl $host/large > /tmp/large
 *     文件应当能够正常下载
 *   3. 使用curl访问 POST /validate 接口
 *     curl -X POST $host/validate --data @/tmp/large
 *     应当响应 "OK"
 *   4. 退出SwitchTCP
 *     使用kill命令，或者使用curl -X POST $host/exit --data '1'
 *
 * 流量路径:
 *   外部客户端 → TUN 设备 (OS 网口) → vSwitch (VLAN 3) → HTTP Server (:80)
 *
 * 网络拓扑:
 *   TUN 设备:      utun17 (macOS) 或 tun17 (Linux)，通过 NetworkInterface 动态查找可用名
 *   TUN MAC:       00:00:00:00:03:04，addTun 时给 TUN 设备指定的 MAC
 *   TUN 本地地址:   169.254.99.55/24, fd00::99:55/120
 *   vSwitch 网关:   169.254.99.254, fd00::99:fe (MAC: 00:00:00:00:03:05)
 *
 * HTTP 路由:
 *   GET  /hello    → 返回 "world"
 *   GET  /large    → 返回 30MB 循环字母数据 (a-zA-Z0-9)
 *   POST /validate → 校验请求体是否与 /large 返回的数据完全一致
 *   POST /exit     → 接收正整数 N，回复后等待 N 秒退出进程（用于测试后自动清理）
 *
 * 示例:
 *   curl http://169.254.99.254/hello          # IPv4 访问
 *   curl 'http://[fd00::99:fe]/hello'       # IPv6 访问
 */
object SwitchTCP {
  @Throws(Exception::class)
  @JvmStatic
  fun main(args: Array<String>) {
    Utils.loadDynamicLibrary("pni")

    val largeBuffer = ByteArray.allocate(30 * 1024 * 1024) // 30M
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toByteArray()
    for (i in 0 until largeBuffer.length()) {
      largeBuffer[i] = chars[i % chars.size]
    }

    val elg = EventLoopGroup("elg0")
    val sw = Switch(
      "sw0",
      IPPort("127.0.0.1", 18472),
      elg,
      60000,
      60000,
      SecurityGroup.allowAll()
    )
    sw.start()
    elg.add("el0")
    val el = elg["el0"]
    val loop = el.selectorEventLoop
    val existingIfaces = mutableSetOf<String>()
    val ifaces = NetworkInterface.getNetworkInterfaces()
    while (ifaces.hasMoreElements()) {
      existingIfaces.add(ifaces.nextElement().name)
    }
    val prefix = if (OS.isMac()) "utun" else "tun"
    var num = 17
    while (existingIfaces.contains("$prefix$num")) {
      num++
    }
    val tunDev = "$prefix$num"
    val script = if (OS.isMac()) {
      """
        sudo ifconfig $tunDev 169.254.99.55 169.254.99.254 netmask 255.255.255.0 up
        sudo ifconfig $tunDev inet6 fd00::99:55 prefixlen 120
        """.trimIndent()
    } else {
      """
        sudo ip addr add 169.254.99.55/24 dev $tunDev
        sudo ip -6 addr add fd00::99:55/120 dev $tunDev
        """.trimIndent()
    }
    val f = File.createTempFile(tunDev, ".sh")
    f.deleteOnExit()
    FileOutputStream(f).use { fos ->
      fos.write(script.toByteArray())
      fos.flush()
    }
    f.setExecutable(true)
    sw.addNetwork(
      3,
      Network.from("169.254.99.0/24"),
      Network.from("[fd00::99:0]/120"), null
    )
    sw.addTun(tunDev, 3, MacAddress("00:00:00:00:03:04"), f.absolutePath)
    val network = sw.getNetwork(3)
    network.addIp(
      IP.from("169.254.99.254"),
      MacAddress("00:00:00:00:03:05"), null
    )
    network.addIp(
      IP.from("fd00::99:fe"),
      MacAddress("00:00:00:00:03:05"), null
    )
    val fds = network.fds()
    val serverSock = ServerSock.create(IPPort("0.0.0.0", 80), fds)
    val serverSock6 = ServerSock.create(IPPort("::", 80), fds)

    val httpServer4 = CoroutineHttp1Server(serverSock.coroutine(el))
    val httpServer6 = CoroutineHttp1Server(serverSock6.coroutine(el))

    for (httpServer in listOf(httpServer4, httpServer6)) {
      httpServer.get("/hello") { it.conn.response(200).send("world\r\n") }
      httpServer.get("/large") { it.conn.response(200).send(largeBuffer) }
      httpServer.post("/validate") {
        val body = it.req.body()
        if (body.length() == 0) {
          it.conn.response(400).send("body not provided\r\n")
          return@post
        }
        if (body.length() != largeBuffer.length()) {
          it.conn.response(400).send("body length is not ${largeBuffer.length()}\r\n")
          return@post
        }
        for (i in 0 until largeBuffer.length()) {
          if (body[i] != largeBuffer[i]) {
            it.conn.response(400).send(
              "invalid char at index $i, expecting ${largeBuffer[i].toInt().toChar()}, but got ${body[i].toInt().toChar()}"
            )
            return@post
          }
        }
        it.conn.response(200).send("OK\r\n")
      }
      httpServer.post("/exit") {
        val body = it.req.body().toString()
        if (!Utils.isInteger(body)) {
          it.conn.response(400).send("body: `$body` is not an integer\r\n")
          return@post
        }
        val seconds = Integer.parseInt(body)
        if (seconds <= 0) {
          it.conn.response(400).send("expecting positive number, but got $seconds\r\n")
          return@post
        }
        it.conn.response(200).send("will exit after ${seconds}s\r\n")
        sleep(seconds * 1000)
        exitProcess(0)
      }
      loop.launch {
        httpServer.start()
      }
    }
  }
}
