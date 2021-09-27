package io.vproxy.test.cases

import kotlinx.coroutines.channels.Channel
import org.junit.*
import io.vproxy.base.connection.ConnectableConnection
import io.vproxy.base.connection.ConnectionOpts
import io.vproxy.base.connection.ServerSock
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.ByteArray
import io.vproxy.base.util.RingBuffer
import io.vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.*
import vproxy.lib.tcp.CoroutineServerSock
import io.vproxy.test.tool.Client
import io.vproxy.vfd.IPPort
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class TestNetServerClient {
  companion object {
    private const val listenPort = 30080
    private var loop: _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop? = null

    @JvmStatic
    @BeforeClass
    fun beforeClass() {
      val loop = _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.open()
      this.loop = loop
      loop.loop { _root_ide_package_.io.vproxy.base.util.thread.VProxyThread.create(it, "test-net-server-client") }
    }

    @JvmStatic
    @AfterClass
    fun afterClass() {
      loop!!.close()
    }
  }

  private var server: CoroutineServerSock? = null

  @Before
  fun setUp() {
    server = _root_ide_package_.io.vproxy.base.connection.ServerSock.create(
      _root_ide_package_.io.vproxy.vfd.IPPort(
        "127.0.0.1",
        listenPort
      )
    ).coroutine(loop!!.ensureNetEventLoop())
    Thread.sleep(500)
  }

  @After
  fun tearDown() {
    server!!.close()
  }

  @Test
  fun simpleServer() {
    val channel = Channel<String>()
    val promise = loop!!.execute {
      val conn = server!!.accept()
      vplib.coroutine.with(conn).launch {
        val buf = _root_ide_package_.io.vproxy.base.util.ByteArray.allocate(1024)
        while (true) {
          val n = conn.read(buf)
          val data = buf.sub(0, n)

          val str = String(data.toJavaArray())
          if (str == "quit\r\n") {
            conn.close()
            channel.close()
            break
          }

          conn.write(data)
          channel.send(str)
        }
      }
      val sb = StringBuilder()
      for (s in channel) {
        sb.append(s)
      }
      sb.toString()
    }

    val client = _root_ide_package_.io.vproxy.test.tool.Client(listenPort)
    client.connect()
    for (s in listOf("hello\r\n", "world\r\n", "foo\r\n", "bar\r\n")) {
      Assert.assertEquals(s, client.sendAndRecv(s, s.length))
    }
    client.sendAndRecv("quit\r\n", 0)
    val res = promise.block()
    Assert.assertEquals("hello\r\nworld\r\nfoo\r\nbar\r\n", res)
  }

  @Test
  fun simpleClient() {
    loop!!.launch {
      while (true) {
        val conn = server!!.accept()
        vplib.coroutine.with(conn).launch {
          val buf = _root_ide_package_.io.vproxy.base.util.ByteArray.allocate(1024)
          while (true) {
            val n = conn.read(buf)
            if (n == 0) {
              break
            }
            val data = buf.sub(0, n)
            conn.write(data)
          }
        }
      }
    }

    val dataQ = LinkedBlockingDeque<String>()
    loop!!.launch {
      val client = unsafeIO {
        _root_ide_package_.io.vproxy.base.connection.ConnectableConnection.create(
          _root_ide_package_.io.vproxy.vfd.IPPort("127.0.0.1", listenPort), _root_ide_package_.io.vproxy.base.connection.ConnectionOpts(),
          _root_ide_package_.io.vproxy.base.util.RingBuffer.allocate(1024), _root_ide_package_.io.vproxy.base.util.RingBuffer.allocate(1024)
        ).coroutine(loop!!.ensureNetEventLoop())
      }
      defer { client.close() }
      client.connect()
      val buf = _root_ide_package_.io.vproxy.base.util.ByteArray.allocate(1024)
      for (s in listOf("hello", "world", "foo", "bar")) {
        client.write(s)
        val n = client.read(buf)
        dataQ.add(String(buf.sub(0, n).toJavaArray()))
        sleep(10)
      }
    }

    for (s in listOf("hello", "world", "foo", "bar")) {
      val r: String? = dataQ.poll(1000, TimeUnit.MILLISECONDS)
      Assert.assertEquals(s, r)
    }
  }
}
