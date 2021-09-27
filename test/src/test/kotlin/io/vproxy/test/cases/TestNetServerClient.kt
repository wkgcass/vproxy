package io.vproxy.test.cases

import io.vproxy.lib.common.*
import io.vproxy.lib.tcp.CoroutineServerSock
import kotlinx.coroutines.channels.Channel
import org.junit.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class TestNetServerClient {
  companion object {
    private const val listenPort = 30080
    private var loop: io.vproxy.base.selector.SelectorEventLoop? = null

    @JvmStatic
    @BeforeClass
    fun beforeClass() {
      val loop = io.vproxy.base.selector.SelectorEventLoop.open()
      this.loop = loop
      loop.loop { io.vproxy.base.util.thread.VProxyThread.create(it, "test-net-server-client") }
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
    server = io.vproxy.base.connection.ServerSock.create(
      io.vproxy.vfd.IPPort(
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
        val buf = io.vproxy.base.util.ByteArray.allocate(1024)
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

    val client = io.vproxy.test.tool.Client(listenPort)
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
          val buf = io.vproxy.base.util.ByteArray.allocate(1024)
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
        io.vproxy.base.connection.ConnectableConnection.create(
          io.vproxy.vfd.IPPort("127.0.0.1", listenPort), io.vproxy.base.connection.ConnectionOpts(),
          io.vproxy.base.util.RingBuffer.allocate(1024), io.vproxy.base.util.RingBuffer.allocate(1024)
        ).coroutine(loop!!.ensureNetEventLoop())
      }
      defer { client.close() }
      client.connect()
      val buf = io.vproxy.base.util.ByteArray.allocate(1024)
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
