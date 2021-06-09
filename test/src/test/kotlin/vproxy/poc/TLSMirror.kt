package vproxy.poc

import vjson.JSON
import vjson.util.ObjectBuilder
import vproxy.base.dns.Resolver
import vproxy.base.util.OS
import vproxy.lib.http1.CoroutineHttp1ClientConnection
import vproxy.vmirror.Mirror
import java.io.File
import java.io.FileOutputStream

object TLSMirror {
  @Throws(Exception::class)
  @JvmStatic
  fun main(args: Array<String>) {
    if (OS.isWindows()) {
      System.setProperty("vproxy/vfd", "windows")
    } else {
      System.setProperty("vproxy/vfd", "posix")
    }
    val config: JSON.Instance<*> = ObjectBuilder()
      .put("enabled", true)
      .putArray("mirrors") {
        addObject {
          put("tap", "tap3")
          put("mtu", 47)
          putArray("origins") {
            addObject {
              put("origin", "ssl")
              putArray("filters") {
                addObject { }
              }
            }
          }
        }
      }
      .build()
    val tmpF = File.createTempFile("mirror", ".json")
    val fos = FileOutputStream(tmpF)
    fos.write(config.stringify().toByteArray())
    fos.flush()
    fos.close()
    Mirror.init(tmpF.absolutePath)
    println("wait for 10 seconds before start")
    Thread.sleep(5000)
    println("wait for 5 seconds before start")
    Thread.sleep(1000)
    println("wait for 4 seconds before start")
    Thread.sleep(1000)
    println("wait for 3 seconds before start")
    Thread.sleep(1000)
    println("wait for 2 seconds before start")
    Thread.sleep(1000)
    println("wait for 1 seconds before start")
    Thread.sleep(1000)
    println("start")

    CoroutineHttp1ClientConnection.simpleGet("https://www.baidu.com").block()
    Thread.sleep(1000)
    Resolver.stopDefault()
    Mirror.destroy()
  }
}
