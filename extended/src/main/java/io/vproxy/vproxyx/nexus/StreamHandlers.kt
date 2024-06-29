package io.vproxy.vproxyx.nexus

import io.vproxy.base.connection.ConnectableConnection
import io.vproxy.base.connection.Connection
import io.vproxy.base.connection.ConnectionOpts
import io.vproxy.base.connection.ServerSock
import io.vproxy.base.processor.http1.entity.Header
import io.vproxy.base.processor.http1.entity.Request
import io.vproxy.base.selector.wrap.quic.QuicSocketFD
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.RingBuffer
import io.vproxy.component.proxy.Proxy
import io.vproxy.component.proxy.Session
import io.vproxy.lib.common.launch
import io.vproxy.lib.common.sleep
import io.vproxy.lib.http1.CoroutineHttp1ClientConnection
import io.vproxy.lib.http1.CoroutineHttp1ServerConnection
import io.vproxy.lib.tcp.CoroutineConnection
import io.vproxy.lib.tcp.CoroutineServerSock
import io.vproxy.vfd.FDProvider
import io.vproxy.vfd.IPPort
import io.vproxy.vproxyx.nexus.entity.LinkPeer
import io.vproxy.vproxyx.nexus.entity.LinkReq
import vjson.JSON
import vjson.util.ObjectBuilder
import java.io.IOException
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max

object StreamHandlers {
  fun handleAccepted(nctx: NexusContext, peer: NexusPeer, fd: QuicSocketFD) {
    val conn = Connection.wrap(
      fd, ConnectionOpts().setTimeout(NexusContext.CONTROL_STREAM_TIMEOUT),
      RingBuffer.allocateDirect(16384), RingBuffer.allocateDirect(16384)
    )

    val cosock = CoroutineConnection(nctx.loop, conn)
    val httpconn = CoroutineHttp1ServerConnection(cosock)

    nctx.loop.selectorEventLoop.launch {
      val target: IPPort
      var nextNodeName = ""
      val req: Request?
      try {
        req = httpconn.readRequest()
        if (req == null) {
          Logger.warn(LogType.INVALID_EXTERNAL_DATA, "nothing read from ${peer.remoteAddress}")
          conn.close()
          return@launch
        }
        if (req.method != "CONNECT") {
          defer { peer.terminate(fd.stream.opts.connection, "passive control stream") }
          handleControlRequest(nctx, httpconn, req)
          handlePassiveControlStreamLoop(nctx, httpconn)
          return@launch
        }
        if (!IPPort.validL4AddrStr(req.uri)) {
          Logger.warn(LogType.INVALID_EXTERNAL_DATA, "received invalid request from ${peer.remoteAddress}, invalid uri: ${req.uri}")
          conn.close()
          return@launch
        }
        target = IPPort(req.uri)

        var pendingPath = ""
        var srcNode = "<?>"
        var dstNode = "<?>"
        var traceId = "<?>"
        var clientIP = "<?>"
        var clientPort = "<?>"
        for (h in req.headers) {
          if (h.keyEqualsIgnoreCase("x-nexus-node-path")) {
            val path = h.value.trim()
            if (path.contains(",")) {
              val index = path.indexOf(",")
              nextNodeName = path.substring(0, index).trim()
              h.value = path.substring(index + 1).trim()
            } else {
              nextNodeName = path.trim()
              h.value = ""
            }
            pendingPath = h.value
          } else if (h.keyEqualsIgnoreCase("x-nexus-source-node")) {
            srcNode = h.value.trim()
          } else if (h.keyEqualsIgnoreCase("x-nexus-destination-node")) {
            dstNode = h.value.trim()
          } else if (h.keyEqualsIgnoreCase("x-nexus-trace-id")) {
            traceId = h.value.trim()
          } else if (h.keyEqualsIgnoreCase("x-forwarded-for")) {
            clientIP = h.value.trim()
          } else if (h.keyEqualsIgnoreCase("x-forwarded-port")) {
            clientPort = h.value.trim()
          }
        }

        if (nextNodeName.isEmpty()) {
          Logger.access("proxy(" + traceId + "): client=$clientIP:$clientPort src=$srcNode dst=$dstNode target=${target.formatToIPPortString()}")
        } else if (pendingPath.isEmpty()) {
          Logger.access("proxy(" + traceId + "): client=$clientIP:$clientPort src=$srcNode next=$nextNodeName dst=$dstNode target=${target.formatToIPPortString()}")
        } else {
          Logger.access("proxy(" + traceId + "): client=$clientIP:$clientPort src=$srcNode next=$nextNodeName path=$pendingPath dst=$dstNode target=${target.formatToIPPortString()}")
        }
      } catch (e: Exception) {
        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "failed to handle passive stream from ${peer.remoteAddress}", e)
        conn.close()
        return@launch
      }

      val nextNode = if (nextNodeName.isEmpty()) nctx.nexus.selfNode else nctx.nexus.getNode(nextNodeName)
      if (nextNode == null) {
        httpconn.response(404).send("node $nextNodeName not found")
        return@launch
      }

      handleProxy(nctx, nextNode, target, req, cosock) { code, msg ->
        httpconn.response(code).send(msg)
        if (code != 200) {
          cosock.closeWrite()
        }
      }
    }
  }

  private suspend inline fun handleProxy(
    nctx: NexusContext, nextNode: NexusNode, target: IPPort, req: Request,
    cosock: CoroutineConnection,
    resultCallback: (Int, String) -> Unit
  ) {
    val nextConn: ConnectableConnection
    try {
      val nextCosock: CoroutineConnection
      if (nextNode == nctx.nexus.selfNode) {
        nextConn = ConnectableConnection.create(target)
        nextCosock = CoroutineConnection(nctx.loop, nextConn)
        try {
          nextCosock.connect()
        } catch (e: Exception) {
          nextCosock.close()
          Logger.warn(LogType.CONN_ERROR, "unable to connect to $target", e)
          return resultCallback(400, "unable to connect to $target")
        }
        resultCallback(200, nctx.selfNodeName)
      } else {
        if (nextNode.peer == null) {
          return resultCallback(400, "$nextNode is not directly linked to ${nctx.nexus.selfNode.name}")
        }

        val quicConn = nextNode.peer.quicConnection ?: return resultCallback(400, "node $nextNode is disconnected")

        val nextFD = QuicSocketFD.newStream(quicConn)
        nextConn = ConnectableConnection.wrap(
          nextFD, nextNode.peer.remoteAddress, ConnectionOpts().setTimeout(NexusContext.GENERAL_TIMEOUT),
          RingBuffer.allocateDirect(4096), RingBuffer.allocateDirect(4096)
        )
        nextCosock = CoroutineConnection(nctx.loop, nextConn)
        nextCosock.connect()

        nextCosock.write(req.toByteArray())

        val nextHttpConn = CoroutineHttp1ClientConnection(nextCosock)
        val nextResp = nextHttpConn.readResponse()
        if (nextResp.statusCode != 200) {
          return resultCallback(nextResp.statusCode, nextResp.body.toString())
        }
        resultCallback(200, nextResp.body.toString())
        nextCosock.detach()
      }
      cosock.detach()
    } catch (e: Exception) {
      Logger.warn(LogType.CONN_ERROR, "failed to handle proxy to $target", e)
      return resultCallback(500, "failed to handle proxy")
    }
    doProxy(nctx, cosock.conn, nextConn)
  }

  private fun doProxy(nctx: NexusContext, conn: Connection, target: Connection) {
    try {
      conn.setTimeout(NexusContext.GENERAL_TIMEOUT)
      target.setTimeout(NexusContext.GENERAL_TIMEOUT)

      target.UNSAFE_replaceBuffer(conn.outBuffer, conn.inBuffer, true)

      val session = Session(conn, target)
      nctx.loop.addConnection(conn, null, Proxy.SessionConnectionHandler(session))
      nctx.loop.addConnection(target, null, Proxy.SessionConnectionHandler(session))
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to handle proxy to ${target.remote}", e)
      conn.close()
      target.close()
    }
  }

  fun handleServerSock(nctx: NexusContext, target: IPPort, nodeName: String, sock: ServerSock) {
    val coServerSock = CoroutineServerSock(nctx.loop, sock)
    nctx.loop.selectorEventLoop.launch {
      while (true) {
        val cosock = try {
          coServerSock.accept()
        } catch (e: Throwable) {
          Logger.error(LogType.SOCKET_ERROR, "failed to accept connection from ${sock.bind}", e)
          continue
        }
        if (cosock == null) {
          Logger.warn(LogType.ALERT, "listener ${sock.bind} is terminated")
          break
        }
        // find path
        val dstNode = nctx.nexus.getNode(nodeName)
        if (dstNode == null) {
          Logger.warn(LogType.ALERT, "unable to find dst node $nodeName")
          cosock.close()
          continue
        }
        val path = nctx.nexus.shortestPathTo(dstNode)
        if (path == null) {
          Logger.warn(LogType.ALERT, "unable to find path to dst node $nodeName")
          cosock.close()
          continue
        }
        val nextNode = path.removeFirst()
        val nodePath = path.joinToString(",") { it.name }

        val clientIP = cosock.conn.remote.address.formatToIPString()
        val clientPort = cosock.conn.remote.port.toString()
        val traceId = "nexus-" + Math.abs(ThreadLocalRandom.current().nextLong())
        if (nextNode == nctx.nexus.selfNode) {
          Logger.access("proxy(" + traceId + "): client=$clientIP:$clientPort src=${nctx.selfNodeName} dst=${dstNode.name} target=${target.formatToIPPortString()}")
        } else if (nodePath.isEmpty()) {
          Logger.access("proxy(" + traceId + "): client=$clientIP:$clientPort src=${nctx.selfNodeName} next=${nextNode.name} dst=${dstNode.name} target=${target.formatToIPPortString()}")
        } else {
          Logger.access("proxy(" + traceId + "): client=$clientIP:$clientPort src=${nctx.selfNodeName} next=${nextNode.name} path=$nodePath dst=${dstNode.name} target=${target.formatToIPPortString()}")
        }

        nctx.loop.selectorEventLoop.launch {
          val req = Request()
          req.method = "CONNECT"
          req.uri = target.formatToIPPortString()
          req.headers = listOf(
            Header("X-Nexus-Node-Path", nodePath),
            Header("X-Nexus-Source-Node", nctx.selfNodeName),
            Header("X-Nexus-Destination-Node", dstNode.name),
            Header("X-Nexus-Trace-Id", traceId),
            Header("X-Forwarded-For", clientIP),
            Header("X-Forwarded-Port", clientPort),
          )
          req.version = "HTTP/1.1"
          handleProxy(nctx, nextNode, target, req, cosock) { code, _ -> if (code != 200) cosock.close() }
        }
      }
    }
  }

  fun handleActiveControlStream(nctx: NexusContext, peer: NexusPeer, fd: QuicSocketFD, sendEstablishMsg: Boolean) {
    val conn: ConnectableConnection
    try {
      conn = ConnectableConnection.wrap(
        fd, peer.remoteAddress, ConnectionOpts().setTimeout(NexusContext.CONTROL_STREAM_TIMEOUT),
        RingBuffer.allocateDirect(4096), RingBuffer.allocateDirect(4096)
      )
    } catch (e: IOException) {
      Logger.shouldNotHappen("unable to wrap fd $fd into Connection", e)
      fd.close()
      return
    }

    val cosock = CoroutineConnection(nctx.loop, conn)
    nctx.loop.selectorEventLoop.launch {
      defer { peer.terminate(fd.stream.opts.connection, "active control stream") }

      try {
        cosock.connect()
        val httpconn = CoroutineHttp1ClientConnection(cosock)

        if (sendEstablishMsg) {
          httpconn.post("/ctrl/v1.0/establish").send(
            ObjectBuilder()
              .put("node", nctx.selfNodeName)
              .build()
          )

          val resp = httpconn.readResponse()
          if (resp.statusCode != 200) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid response from ${peer.remoteAddress}: ${resp.statusCode} ${resp.body}")
            return@launch
          }

          val obj = JSON.parse(resp.body.toString()) as JSON.Object
          val remoteNode = obj.getString("node")
          if (NexusUtils.isNotValidNodeName(remoteNode)) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid node name $remoteNode")
            return@launch
          }
          peer.initialize(fd.stream.opts.connection, remoteNode)
        }

        while (true) {
          sleep(2_000)
          val req = LinkReq()
          req.node = nctx.selfNodeName
          req.path = Collections.singletonList(nctx.selfNodeName)
          val links = ArrayList<LinkPeer>()
          for (e in nctx.nexus.selfNode.allEdges()) {
            links.add(LinkPeer(e.to.name, e.distance))
          }
          req.peers = links
          val beginTs = FDProvider.get().nanoTime()
          httpconn.put("/ctrl/v1.0/link").send(req.toJson())
          var resp = httpconn.readResponse()
          val endTs = FDProvider.get().nanoTime()
          if (resp.statusCode != 200 && resp.statusCode != 204) {
            Logger.error(
              LogType.INVALID_EXTERNAL_DATA,
              "failed to update link status $req to ${peer.remoteAddress}: ${resp.statusCode} ${resp.body}"
            )
          }

          // update edge distance
          val deltaTime = max(1, (endTs - beginTs) / 1000)
          val peerNode = peer.node
          if (peerNode != null) {
            for (e in nctx.nexus.selfNode.allEdges()) {
              if (e.to == peerNode) {
                e.distance = deltaTime
              }
            }
          }

          // send received link update events
          for (linkReq in peer.getAndClearLinkUpdateEvents()) {
            httpconn.put("/ctrl/v1.0/link").send(linkReq.toJson())
            resp = httpconn.readResponse()
            if (resp.statusCode != 200 && resp.statusCode != 204) {
              Logger.error(
                LogType.INVALID_EXTERNAL_DATA,
                "failed to forward link status $linkReq to ${peer.remoteAddress}: ${resp.statusCode}: ${resp.body}"
              )
            }
          }
        }
      } catch (e: Exception) {
        Logger.warn(LogType.CONN_ERROR, "failed to handle control data from active stream of ${peer.remoteAddress}", e)
      }
    }
  }

  fun handlePassiveControlStream(nctx: NexusContext, peer: NexusPeer, fd: QuicSocketFD) {
    val conn = Connection.wrap(
      fd, ConnectionOpts().setTimeout(NexusContext.CONTROL_STREAM_TIMEOUT),
      RingBuffer.allocateDirect(4096), RingBuffer.allocateDirect(4096)
    )
    val cosock = CoroutineConnection(nctx.loop, conn)
    val httpconn = CoroutineHttp1ServerConnection(cosock)
    nctx.loop.selectorEventLoop.launch {
      defer { peer.terminate(fd.stream.opts.connection, "passive control stream") }
      try {
        val req = httpconn.readRequest()
        if (req == null) {
          Logger.warn(LogType.INVALID_EXTERNAL_DATA, "nothing read from ${peer.remoteAddress}")
          return@launch
        }
        if (req.method != "POST" || req.uri != "/ctrl/v1.0/establish") {
          Logger.warn(LogType.INVALID_EXTERNAL_DATA, "unexpected request from ${peer.remoteAddress}: ${req.method} ${req.uri}")
          return@launch
        }

        val obj = JSON.parse(req.body.toString()) as JSON.Object
        val remoteNode = obj.getString("node")
        if (NexusUtils.isNotValidNodeName(remoteNode)) {
          Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid node name $remoteNode")
          return@launch
        }

        httpconn.response(200).send(
          ObjectBuilder()
            .put("node", nctx.selfNodeName)
            .build()
        )

        peer.initialize(fd.stream.opts.connection, remoteNode)

        handlePassiveControlStreamLoop(nctx, httpconn)
      } catch (e: Exception) {
        Logger.warn(LogType.CONN_ERROR, "failed to handle control data from passive stream of ${peer.remoteAddress}", e)
      }
    }
  }

  private suspend fun handlePassiveControlStreamLoop(nctx: NexusContext, httpconn: CoroutineHttp1ServerConnection) {
    while (true) {
      val req = httpconn.readRequest()
      if (req == null) {
        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "nothing read from ${httpconn.conn.remote()}")
        return
      }
      handleControlRequest(nctx, httpconn, req)
    }
  }

  private suspend fun handleControlRequest(nctx: NexusContext, httpconn: CoroutineHttp1ServerConnection, req: Request) {
    if (req.method == "PUT" && req.uri == "/ctrl/v1.0/link") {
      handleLinkStatusAdvertisement(nctx, httpconn, req)
    } else if (req.method == "GET" && req.uri == "/ctrl/v1.0/keepalive") {
      httpconn.response(204).send()
    } else {
      Logger.warn(LogType.INVALID_EXTERNAL_DATA, "unexpected request from ${httpconn.conn.remote()}: ${req.method} ${req.uri}")
      httpconn.response(404).send()
    }
    handleLinkStatusAdvertisement(nctx, httpconn, req)
  }

  private suspend fun handleLinkStatusAdvertisement(nctx: NexusContext, httpconn: CoroutineHttp1ServerConnection, req: Request) {
    val linkReq = JSON.deserialize(req.body.toString(), LinkReq.rule)
    val errStr = linkReq.validate()
    if (errStr != null) {
      Logger.warn(LogType.INVALID_EXTERNAL_DATA, "invalid request from ${httpconn.conn.remote()}: $errStr")
      httpconn.response(400).send(errStr)
      return
    }

    nctx.nexus.update(linkReq)

    if (!linkReq.path.contains(nctx.selfNodeName)) {
      linkReq.path.add(nctx.selfNodeName)
      for (e in nctx.nexus.selfNode.allEdges()) {
        if (linkReq.path.contains(e.to.name))
          continue
        e.to.peer.linkUpdateEvent(linkReq)
      }
    }

    httpconn.response(204).send()
  }
}
