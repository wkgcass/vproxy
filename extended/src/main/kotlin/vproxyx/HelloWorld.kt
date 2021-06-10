package vproxyx

import vproxy.base.Config
import vproxy.base.connection.*
import vproxy.base.dhcp.DHCPClientHelper
import vproxy.base.selector.SelectorEventLoop
import vproxy.base.util.*
import vproxy.base.util.nio.ByteArrayChannel
import vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.coroutine
import vproxy.lib.common.launch
import vproxy.lib.common.sleep
import vproxy.lib.common.unsafeIO
import vproxy.lib.http1.CoroutineHttp1ClientConnection
import vproxy.lib.http1.CoroutineHttp1Server
import vproxy.vfd.IP
import vproxy.vfd.IPPort
import vproxy.vfd.SocketFD
import vproxy.vfd.VFDConfig
import java.io.IOException

object HelloWorld {
  @JvmStatic
  @Throws(Exception::class)
  @Suppress("unused_parameter")
  fun main0(args: Array<String?>?) {
    Logger.alert("You are using vproxy " + Version.VERSION)
    val sLoop = SelectorEventLoop.open()
    val nLoop = sLoop.ensureNetEventLoop()
    sLoop.loop { r -> VProxyThread.create(r, "hello-world-main") }
    if (VFDConfig.useFStack) {
      Logger.warn(LogType.ALERT, "DHCP will not run when using FStack")
    } else if (!Config.dhcpGetDnsListEnabled) {
      Logger.alert("Feature 'dhcp to get dns list' NOT enabled.")
      Logger.alert("You may set -DhcpGetDnsListNics=all or eth0,eth1,... to enable the feature.")
    } else {
      Logger.alert("Retrieving dns servers using DHCP ...")
      val cb: BlockCallback<Set<IP>, IOException> = BlockCallback<Set<IP>, IOException>()
      DHCPClientHelper.getDomainNameServers(sLoop, Config.dhcpGetDnsListNics, 1, cb)
      try {
        val ips: Set<IP> = cb.block()
        Logger.alert("dhcp returns with dns servers: $ips")
      } catch (e: IOException) {
        Logger.warn(LogType.ALERT, "failed to retrieve dns servers from dhcp", e)
      }
    }
    val listenPort = 8080
    sLoop.launch {
      val httpServerSock = unsafeIO { ServerSock.create(IPPort("0.0.0.0", listenPort)).coroutine() }
      val server = CoroutineHttp1Server(httpServerSock)
      server
        .get("/") { it.conn.response(200).send("vproxy ${Version.VERSION}\r\n") }
        .get("/hello") {
          it.conn.response(200).send(
            "Welcome to vproxy ${Version.VERSION}.\r\n" +
              "Your request address is ${it.conn.base().remote.formatToIPPortString()}.\r\n" +
              "Server address is ${it.conn.base().local.formatToIPPortString()}.\r\n"
          )
        }
      server.start()
    }
    Logger.alert("HTTP server is listening on $listenPort")
    if (VFDConfig.useFStack) {
      Logger.warn(LogType.ALERT, "F-Stack does not support 127.0.0.1 nor to request self ip address.")
      Logger.warn(LogType.ALERT, "You may run `curl \$ip:$listenPort/hello` to see if the TCP server is working.")
      Logger.warn(LogType.ALERT, "Or you may run -Deploy=Simple to start a simple loadbalancer to verify the TCP client functions.")
    } else {
      sLoop.launch {
        sleep(1000)
        Logger.alert("HTTP client now starts ...")
        Logger.alert("Making request: GET /hello")

        val client = CoroutineHttp1ClientConnection.create(IPPort("127.0.0.1", listenPort))
        defer { client.close() }
        client.get("/hello").send()
        val resp = client.readResponse()
        Logger.alert("Server responds:\n${resp.body}")
        Logger.alert("TCP seems OK")
      }
    }
    val listenAddress = IPPort(IP.from(byteArrayOf(0, 0, 0, 0)), listenPort)
    val connectAddress = IPPort(IP.from(byteArrayOf(127, 0, 0, 1)), listenPort)
    val bufferSize = 1024
    val sock: ServerSock = ServerSock.createUDP(listenAddress, sLoop)
    nLoop.addServer(sock, null, object : ServerHandler {
      override fun acceptFail(ctx: ServerHandlerContext, err: IOException) {
        Logger.error(LogType.ALERT, "Accept($sock) failed", err)
      }

      override fun connection(ctx: ServerHandlerContext, connection: Connection) {
        try {
          nLoop.addConnection(connection, null, object : ConnectionHandler {
            override fun readable(ctx: ConnectionHandlerContext) {
              // ignore, the buffers are piped
            }

            override fun writable(ctx: ConnectionHandlerContext) {
              // ignore, the buffers are piped
            }

            override fun exception(ctx: ConnectionHandlerContext, err: IOException) {
              Logger.error(LogType.ALERT, "Connection $connection got exception ", err)
              ctx.connection.close()
            }

            override fun remoteClosed(ctx: ConnectionHandlerContext) {
              ctx.connection.close()
            }

            override fun closed(ctx: ConnectionHandlerContext) {
              // ignore
            }

            override fun removed(ctx: ConnectionHandlerContext) {
              // ignore
            }
          })
        } catch (e: IOException) {
          Logger.error(LogType.ALERT, "adding connection $connection from $sock to event loop failed", e)
        }
      }

      override fun getIOBuffers(channel: SocketFD): Tuple<RingBuffer, RingBuffer> {
        val buf = RingBuffer.allocateDirect(bufferSize)
        return Tuple<RingBuffer, RingBuffer>(buf, buf) // pipe input to output
      }

      override fun removed(ctx: ServerHandlerContext) {
        Logger.alert("server sock $sock removed from loop")
      }
    })
    Logger.alert("UDP server is listening on $listenPort")
    if (VFDConfig.useFStack) {
      Logger.warn(
        LogType.ALERT,
        "You may run `nc -u \$ip $listenPort` to see if the UDP server is working. It's an echo server, it will respond with anything you input."
      )
    } else {
      sLoop.delay(2000) {
        Logger.alert("UDP client now starts ...")
        try {
          val conn: ConnectableConnection = ConnectableConnection.createUDP(
            connectAddress,
            ConnectionOpts(),
            RingBuffer.allocateDirect(bufferSize),
            RingBuffer.allocateDirect(bufferSize)
          )
          nLoop.addConnectableConnection(conn, null, object : ConnectableConnectionHandler {
            private val message = "hello world"
            override fun connected(ctx: ConnectableConnectionHandlerContext) {
              // send data when connected
              val str = message
              Logger.alert("UDP client sends a message to server: $str")
              ctx.connection.outBuffer.storeBytesFrom(ByteArrayChannel.fromFull(str.toByteArray()))
            }

            override fun readable(ctx: ConnectionHandlerContext) {
              val chnl = ByteArrayChannel.fromEmpty(bufferSize)
              val len: Int = ctx.connection.inBuffer.writeTo(chnl)
              val str = String(chnl.bytes, 0, len)
              Logger.alert("UDP client receives a message from server: $str")
              if (str == message) {
                Logger.alert("UDP seems OK")
                ctx.connection.close()
              } else {
                Logger.error(LogType.ALERT, "received message is not complete")
              }
            }

            override fun writable(ctx: ConnectionHandlerContext) {
              // ignore
            }

            override fun exception(ctx: ConnectionHandlerContext, err: IOException) {
              Logger.error(LogType.ALERT, "Connection $conn got exception ", err)
            }

            override fun remoteClosed(ctx: ConnectionHandlerContext) {
              ctx.connection.close()
            }

            override fun closed(ctx: ConnectionHandlerContext) {
              // ignore
            }

            override fun removed(ctx: ConnectionHandlerContext) {
              // ignore
            }
          })
        } catch (e: IOException) {
          Logger.error(LogType.ALERT, "Initiating UDP Client failed", e)
        }
      }
    }
  }
}
