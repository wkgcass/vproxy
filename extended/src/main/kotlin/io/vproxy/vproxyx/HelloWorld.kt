package io.vproxy.vproxyx

import io.vproxy.base.Config
import vproxy.base.connection.*
import io.vproxy.base.dhcp.DHCPClientHelper
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.RingBuffer
import io.vproxy.base.util.Version
import io.vproxy.base.util.callback.BlockCallback
import vproxy.base.util.coll.Tuple
import io.vproxy.base.util.nio.ByteArrayChannel
import io.vproxy.base.util.thread.VProxyThread
import vproxy.lib.common.coroutine
import vproxy.lib.common.launch
import vproxy.lib.common.sleep
import vproxy.lib.common.unsafeIO
import vproxy.lib.http1.CoroutineHttp1ClientConnection
import vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPPort
import io.vproxy.vfd.SocketFD
import io.vproxy.vfd.VFDConfig
import java.io.IOException

object HelloWorld {
  @JvmStatic
  @Throws(Exception::class)
  @Suppress("unused_parameter")
  fun main0(args: Array<String?>?) {
    _root_ide_package_.io.vproxy.base.util.Logger.alert("You are using vproxy " + _root_ide_package_.io.vproxy.base.util.Version.VERSION)
    val sLoop = _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.open()
    val nLoop = sLoop.ensureNetEventLoop()
    sLoop.loop { r -> _root_ide_package_.io.vproxy.base.util.thread.VProxyThread.create(r, "hello-world-main") }
    if (_root_ide_package_.io.vproxy.vfd.VFDConfig.useFStack) {
      _root_ide_package_.io.vproxy.base.util.Logger.warn(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "DHCP will not run when using FStack")
    } else if (!_root_ide_package_.io.vproxy.base.Config.dhcpGetDnsListEnabled) {
      _root_ide_package_.io.vproxy.base.util.Logger.alert("Feature 'dhcp to get dns list' NOT enabled.")
      _root_ide_package_.io.vproxy.base.util.Logger.alert("You may set -DhcpGetDnsListNics=all or eth0,eth1,... to enable the feature.")
    } else {
      _root_ide_package_.io.vproxy.base.util.Logger.alert("Retrieving dns servers using DHCP ...")
      val cb: _root_ide_package_.io.vproxy.base.util.callback.BlockCallback<Set<_root_ide_package_.io.vproxy.vfd.IP>, IOException> =
        _root_ide_package_.io.vproxy.base.util.callback.BlockCallback<Set<_root_ide_package_.io.vproxy.vfd.IP>, IOException>()
      _root_ide_package_.io.vproxy.base.dhcp.DHCPClientHelper.getDomainNameServers(sLoop, _root_ide_package_.io.vproxy.base.Config.dhcpGetDnsListNics, 1, cb)
      try {
        val ips: Set<_root_ide_package_.io.vproxy.vfd.IP> = cb.block()
        _root_ide_package_.io.vproxy.base.util.Logger.alert("dhcp returns with dns servers: $ips")
      } catch (e: IOException) {
        _root_ide_package_.io.vproxy.base.util.Logger.warn(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "failed to retrieve dns servers from dhcp", e)
      }
    }
    val listenPort = 8080
    sLoop.launch {
      val httpServerSock = unsafeIO { _root_ide_package_.io.vproxy.base.connection.ServerSock.create(
        _root_ide_package_.io.vproxy.vfd.IPPort(
          "0.0.0.0",
          listenPort
        )
      ).coroutine() }
      val server = CoroutineHttp1Server(httpServerSock)
      server
        .get("/") { it.conn.response(200).send("vproxy ${_root_ide_package_.io.vproxy.base.util.Version.VERSION}\r\n") }
        .get("/hello") {
          it.conn.response(200).send(
            "Welcome to vproxy ${_root_ide_package_.io.vproxy.base.util.Version.VERSION}.\r\n" +
              "Your request address is ${it.conn.base().remote.formatToIPPortString()}.\r\n" +
              "Server address is ${it.conn.base().local.formatToIPPortString()}.\r\n"
          )
        }
      server.start()
    }
    _root_ide_package_.io.vproxy.base.util.Logger.alert("HTTP server is listening on $listenPort")
    if (_root_ide_package_.io.vproxy.vfd.VFDConfig.useFStack) {
      _root_ide_package_.io.vproxy.base.util.Logger.warn(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "F-Stack does not support 127.0.0.1 nor to request self ip address.")
      _root_ide_package_.io.vproxy.base.util.Logger.warn(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "You may run `curl \$ip:$listenPort/hello` to see if the TCP server is working.")
      _root_ide_package_.io.vproxy.base.util.Logger.warn(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "Or you may run -Deploy=Simple to start a simple loadbalancer to verify the TCP client functions.")
    } else {
      sLoop.launch {
        sleep(1000)
        _root_ide_package_.io.vproxy.base.util.Logger.alert("HTTP client now starts ...")
        _root_ide_package_.io.vproxy.base.util.Logger.alert("Making request: GET /hello")

        val client = CoroutineHttp1ClientConnection.create(_root_ide_package_.io.vproxy.vfd.IPPort("127.0.0.1", listenPort))
        defer { client.close() }
        client.get("/hello").send()
        val resp = client.readResponse()
        _root_ide_package_.io.vproxy.base.util.Logger.alert("Server responds:\n${resp.body}")
        _root_ide_package_.io.vproxy.base.util.Logger.alert("TCP seems OK")
      }
    }
    val listenAddress = _root_ide_package_.io.vproxy.vfd.IPPort(IP.from(byteArrayOf(0, 0, 0, 0)), listenPort)
    val connectAddress = _root_ide_package_.io.vproxy.vfd.IPPort(IP.from(byteArrayOf(127, 0, 0, 1)), listenPort)
    val bufferSize = 1024
    val sock: _root_ide_package_.io.vproxy.base.connection.ServerSock = _root_ide_package_.io.vproxy.base.connection.ServerSock.createUDP(listenAddress, sLoop)
    nLoop.addServer(sock, null, object : _root_ide_package_.io.vproxy.base.connection.ServerHandler {
      override fun acceptFail(ctx: _root_ide_package_.io.vproxy.base.connection.ServerHandlerContext, err: IOException) {
        _root_ide_package_.io.vproxy.base.util.Logger.error(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "Accept($sock) failed", err)
      }

      override fun connection(ctx: _root_ide_package_.io.vproxy.base.connection.ServerHandlerContext, connection: _root_ide_package_.io.vproxy.base.connection.Connection) {
        try {
          nLoop.addConnection(connection, null, object : _root_ide_package_.io.vproxy.base.connection.ConnectionHandler {
            override fun readable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore, the buffers are piped
            }

            override fun writable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore, the buffers are piped
            }

            override fun exception(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext, err: IOException) {
              _root_ide_package_.io.vproxy.base.util.Logger.error(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "Connection $connection got exception ", err)
              ctx.connection.close()
            }

            override fun remoteClosed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              ctx.connection.close()
            }

            override fun closed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }

            override fun removed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }
          })
        } catch (e: IOException) {
          _root_ide_package_.io.vproxy.base.util.Logger.error(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "adding connection $connection from $sock to event loop failed", e)
        }
      }

      override fun getIOBuffers(channel: _root_ide_package_.io.vproxy.vfd.SocketFD): Tuple<_root_ide_package_.io.vproxy.base.util.RingBuffer, _root_ide_package_.io.vproxy.base.util.RingBuffer> {
        val buf = _root_ide_package_.io.vproxy.base.util.RingBuffer.allocateDirect(bufferSize)
        return Tuple<_root_ide_package_.io.vproxy.base.util.RingBuffer, _root_ide_package_.io.vproxy.base.util.RingBuffer>(buf, buf) // pipe input to output
      }

      override fun removed(ctx: _root_ide_package_.io.vproxy.base.connection.ServerHandlerContext) {
        _root_ide_package_.io.vproxy.base.util.Logger.alert("server sock $sock removed from loop")
      }
    })
    _root_ide_package_.io.vproxy.base.util.Logger.alert("UDP server is listening on $listenPort")
    if (_root_ide_package_.io.vproxy.vfd.VFDConfig.useFStack) {
      _root_ide_package_.io.vproxy.base.util.Logger.warn(
        _root_ide_package_.io.vproxy.base.util.LogType.ALERT,
        "You may run `nc -u \$ip $listenPort` to see if the UDP server is working. It's an echo server, it will respond with anything you input."
      )
    } else {
      sLoop.delay(2000) {
        _root_ide_package_.io.vproxy.base.util.Logger.alert("UDP client now starts ...")
        try {
          val conn: _root_ide_package_.io.vproxy.base.connection.ConnectableConnection = _root_ide_package_.io.vproxy.base.connection.ConnectableConnection.createUDP(
            connectAddress,
            _root_ide_package_.io.vproxy.base.connection.ConnectionOpts(),
            _root_ide_package_.io.vproxy.base.util.RingBuffer.allocateDirect(bufferSize),
            _root_ide_package_.io.vproxy.base.util.RingBuffer.allocateDirect(bufferSize)
          )
          nLoop.addConnectableConnection(conn, null, object : _root_ide_package_.io.vproxy.base.connection.ConnectableConnectionHandler {
            private val message = "hello world"
            override fun connected(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectableConnectionHandlerContext) {
              // send data when connected
              val str = message
              _root_ide_package_.io.vproxy.base.util.Logger.alert("UDP client sends a message to server: $str")
              ctx.connection.outBuffer.storeBytesFrom(_root_ide_package_.io.vproxy.base.util.nio.ByteArrayChannel.fromFull(str.toByteArray()))
            }

            override fun readable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              val chnl = _root_ide_package_.io.vproxy.base.util.nio.ByteArrayChannel.fromEmpty(bufferSize)
              val len: Int = ctx.connection.inBuffer.writeTo(chnl)
              val str = String(chnl.bytes, 0, len)
              _root_ide_package_.io.vproxy.base.util.Logger.alert("UDP client receives a message from server: $str")
              if (str == message) {
                _root_ide_package_.io.vproxy.base.util.Logger.alert("UDP seems OK")
                ctx.connection.close()
              } else {
                _root_ide_package_.io.vproxy.base.util.Logger.error(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "received message is not complete")
              }
            }

            override fun writable(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }

            override fun exception(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext, err: IOException) {
              _root_ide_package_.io.vproxy.base.util.Logger.error(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "Connection $conn got exception ", err)
            }

            override fun remoteClosed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              ctx.connection.close()
            }

            override fun closed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }

            override fun removed(ctx: _root_ide_package_.io.vproxy.base.connection.ConnectionHandlerContext) {
              // ignore
            }
          })
        } catch (e: IOException) {
          _root_ide_package_.io.vproxy.base.util.Logger.error(_root_ide_package_.io.vproxy.base.util.LogType.ALERT, "Initiating UDP Client failed", e)
        }
      }
    }
  }
}
