package io.vproxy.vproxyx

import io.vproxy.base.connection.*
import io.vproxy.base.util.coll.Tuple
import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.launch
import io.vproxy.lib.common.sleep
import io.vproxy.lib.common.unsafeIO
import io.vproxy.lib.http1.CoroutineHttp1ClientConnection
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vfd.IP
import java.io.IOException

object HelloWorld {
  @JvmStatic
  @Throws(Exception::class)
  @Suppress("unused_parameter")
  fun main0(args: Array<String?>?) {
    io.vproxy.base.util.Logger.alert("You are using vproxy " + io.vproxy.base.util.Version.VERSION)
    val sLoop = io.vproxy.base.selector.SelectorEventLoop.open()
    val nLoop = sLoop.ensureNetEventLoop()
    sLoop.loop { r -> io.vproxy.base.util.thread.VProxyThread.create(r, "hello-world-main") }
    if (io.vproxy.vfd.VFDConfig.useFStack) {
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.ALERT, "DHCP will not run when using FStack")
    } else if (!io.vproxy.base.Config.dhcpGetDnsListEnabled) {
      io.vproxy.base.util.Logger.alert("Feature 'dhcp to get dns list' NOT enabled.")
      io.vproxy.base.util.Logger.alert("You may set -DhcpGetDnsListNics=all or eth0,eth1,... to enable the feature.")
    } else {
      io.vproxy.base.util.Logger.alert("Retrieving dns servers using DHCP ...")
      val cb: io.vproxy.base.util.callback.BlockCallback<Set<io.vproxy.vfd.IP>, IOException> =
        io.vproxy.base.util.callback.BlockCallback<Set<io.vproxy.vfd.IP>, IOException>()
      io.vproxy.base.dhcp.DHCPClientHelper.getDomainNameServers(sLoop, io.vproxy.base.Config.dhcpGetDnsListNics, 1, cb)
      try {
        val ips: Set<io.vproxy.vfd.IP> = cb.block()
        io.vproxy.base.util.Logger.alert("dhcp returns with dns servers: $ips")
      } catch (e: IOException) {
        io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.ALERT, "failed to retrieve dns servers from dhcp", e)
      }
    }
    val listenPort = 8080
    sLoop.launch {
      val httpServerSock = unsafeIO { io.vproxy.base.connection.ServerSock.create(
        io.vproxy.vfd.IPPort(
          "0.0.0.0",
          listenPort
        )
      ).coroutine() }
      val server = CoroutineHttp1Server(httpServerSock)
      server
        .get("/") { it.conn.response(200).send("vproxy ${io.vproxy.base.util.Version.VERSION}\r\n") }
        .get("/hello") {
          it.conn.response(200).send(
            "Welcome to vproxy ${io.vproxy.base.util.Version.VERSION}.\r\n" +
              "Your request address is ${it.conn.base().remote.formatToIPPortString()}.\r\n" +
              "Server address is ${it.conn.base().local.formatToIPPortString()}.\r\n"
          )
        }
      server.start()
    }
    io.vproxy.base.util.Logger.alert("HTTP server is listening on $listenPort")
    if (io.vproxy.vfd.VFDConfig.useFStack) {
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.ALERT, "F-Stack does not support 127.0.0.1 nor to request self ip address.")
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.ALERT, "You may run `curl \$ip:$listenPort/hello` to see if the TCP server is working.")
      io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.ALERT, "Or you may run -Deploy=Simple to start a simple loadbalancer to verify the TCP client functions.")
    } else {
      sLoop.launch {
        sleep(1000)
        io.vproxy.base.util.Logger.alert("HTTP client now starts ...")
        io.vproxy.base.util.Logger.alert("Making request: GET /hello")

        val client = CoroutineHttp1ClientConnection.create(io.vproxy.vfd.IPPort("127.0.0.1", listenPort))
        defer { client.close() }
        client.get("/hello").send()
        val resp = client.readResponse()
        io.vproxy.base.util.Logger.alert("Server responds:\n${resp.body}")
        io.vproxy.base.util.Logger.alert("TCP seems OK")
      }
    }
    val listenAddress = io.vproxy.vfd.IPPort(IP.from(byteArrayOf(0, 0, 0, 0)), listenPort)
    val connectAddress = io.vproxy.vfd.IPPort(IP.from(byteArrayOf(127, 0, 0, 1)), listenPort)
    val bufferSize = 1024
    val sock: io.vproxy.base.connection.ServerSock = io.vproxy.base.connection.ServerSock.createUDP(listenAddress, sLoop)
    nLoop.addServer(sock, null, object : io.vproxy.base.connection.ServerHandler {
      override fun acceptFail(ctx: io.vproxy.base.connection.ServerHandlerContext, err: IOException) {
        io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.ALERT, "Accept($sock) failed", err)
      }

      override fun connection(ctx: io.vproxy.base.connection.ServerHandlerContext, connection: io.vproxy.base.connection.Connection) {
        try {
          nLoop.addConnection(connection, null, object : io.vproxy.base.connection.ConnectionHandler {
            override fun readable(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore, the buffers are piped
            }

            override fun writable(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore, the buffers are piped
            }

            override fun exception(ctx: io.vproxy.base.connection.ConnectionHandlerContext, err: IOException) {
              io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.ALERT, "Connection $connection got exception ", err)
              ctx.connection.close()
            }

            override fun remoteClosed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              ctx.connection.close()
            }

            override fun closed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }

            override fun removed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }
          })
        } catch (e: IOException) {
          io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.ALERT, "adding connection $connection from $sock to event loop failed", e)
        }
      }

      override fun getIOBuffers(channel: io.vproxy.vfd.SocketFD): Tuple<io.vproxy.base.util.RingBuffer, io.vproxy.base.util.RingBuffer> {
        val buf = io.vproxy.base.util.RingBuffer.allocateDirect(bufferSize)
        return Tuple<io.vproxy.base.util.RingBuffer, io.vproxy.base.util.RingBuffer>(buf, buf) // pipe input to output
      }

      override fun removed(ctx: io.vproxy.base.connection.ServerHandlerContext) {
        io.vproxy.base.util.Logger.alert("server sock $sock removed from loop")
      }
    })
    io.vproxy.base.util.Logger.alert("UDP server is listening on $listenPort")
    if (io.vproxy.vfd.VFDConfig.useFStack) {
      io.vproxy.base.util.Logger.warn(
        io.vproxy.base.util.LogType.ALERT,
        "You may run `nc -u \$ip $listenPort` to see if the UDP server is working. It's an echo server, it will respond with anything you input."
      )
    } else {
      sLoop.delay(2000) {
        io.vproxy.base.util.Logger.alert("UDP client now starts ...")
        try {
          val conn: io.vproxy.base.connection.ConnectableConnection = io.vproxy.base.connection.ConnectableConnection.createUDP(
            connectAddress,
            io.vproxy.base.connection.ConnectionOpts(),
            io.vproxy.base.util.RingBuffer.allocateDirect(bufferSize),
            io.vproxy.base.util.RingBuffer.allocateDirect(bufferSize)
          )
          nLoop.addConnectableConnection(conn, null, object : io.vproxy.base.connection.ConnectableConnectionHandler {
            private val message = "hello world"
            override fun connected(ctx: io.vproxy.base.connection.ConnectableConnectionHandlerContext) {
              // send data when connected
              val str = message
              io.vproxy.base.util.Logger.alert("UDP client sends a message to server: $str")
              ctx.connection.outBuffer.storeBytesFrom(io.vproxy.base.util.nio.ByteArrayChannel.fromFull(str.toByteArray()))
            }

            override fun readable(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              val chnl = io.vproxy.base.util.nio.ByteArrayChannel.fromEmpty(bufferSize)
              val len: Int = ctx.connection.inBuffer.writeTo(chnl)
              val str = String(chnl.bytes, 0, len)
              io.vproxy.base.util.Logger.alert("UDP client receives a message from server: $str")
              if (str == message) {
                io.vproxy.base.util.Logger.alert("UDP seems OK")
                ctx.connection.close()
              } else {
                io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.ALERT, "received message is not complete")
              }
            }

            override fun writable(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }

            override fun exception(ctx: io.vproxy.base.connection.ConnectionHandlerContext, err: IOException) {
              io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.ALERT, "Connection $conn got exception ", err)
            }

            override fun remoteClosed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              ctx.connection.close()
            }

            override fun closed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }

            override fun removed(ctx: io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }
          })
        } catch (e: IOException) {
          io.vproxy.base.util.Logger.error(io.vproxy.base.util.LogType.ALERT, "Initiating UDP Client failed", e)
        }
      }
    }
  }
}
