package vproxy.app.controller

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import vjson.JSON
import vjson.simple.SimpleArray
import vjson.util.ArrayBuilder
import vjson.util.ObjectBuilder
import vproxy.app.app.Application
import vproxy.base.Config
import vproxy.base.GlobalEvents
import vproxy.base.component.elgroup.EventLoopGroup
import vproxy.base.component.svrgroup.ServerGroup
import vproxy.base.connection.ServerSock
import vproxy.base.dns.Cache
import vproxy.base.dns.Resolver
import vproxy.base.selector.SelectorEventLoop
import vproxy.base.util.LogType
import vproxy.base.util.Logger
import vproxy.base.util.callback.Callback
import vproxy.base.util.exception.AlreadyExistException
import vproxy.base.util.exception.NotFoundException
import vproxy.base.util.exception.XException
import vproxy.base.util.web.ClasspathResourceHolder
import vproxy.component.app.Socks5Server
import vproxy.component.app.TcpLB
import vproxy.component.secure.SecurityGroupRule
import vproxy.component.ssl.CertKey
import vproxy.component.svrgroup.Upstream
import vproxy.dns.DNSServer
import vproxy.lib.common.coroutine
import vproxy.lib.common.launch
import vproxy.lib.http.RoutingContext
import vproxy.lib.http.RoutingHandler
import vproxy.lib.http.Tool
import vproxy.lib.http1.CoroutineHttp1Server
import vproxy.vfd.IPPort
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("DuplicatedCode")
class HttpController(val alias: String, val address: IPPort) {
  private val server: CoroutineHttp1Server
  private val classpathResourceHolder = ClasspathResourceHolder("controller/http/webroot")

  init {
    // prepare
    if (Config.checkBind) {
      ServerSock.checkBind(address)
    }
    val sock = ServerSock.create(address)
    val loop = Application.get().controlEventLoop
    server = CoroutineHttp1Server(sock.coroutine(loop))

    // hc
    server.get("/healthz") { ctx -> ctx.conn.response(200).send("OK") }
    // html
    server.get(htmlBase) { ctx -> ctx.conn.response(302).header("Location", "/html/index.html").send() }
    server.get("$htmlBase/*") { ctx ->
      val path: String = ctx.req.uri().substring(htmlBase.length)
      val b = classpathResourceHolder.get(path)
      if (b == null) {
        ctx.conn.response(404).send("Page Not Found\r\n")
      } else {
        ctx.conn.response(200).header("Content-Type", "text/html").send(b)
      }
    }
    // json
    server.all("$apiBase/*", Tool.bodyJsonHandler())
    // all
    server.get(
      "$moduleBase/all",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getAll(rctx, cb) })
    )
    // tcp-lb
    server.get(
      "$moduleBase/tcp-lb/:tl/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getTcpLbDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/tcp-lb/:tl",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getTcpLb(rctx, cb) })
    )
    server.get(
      "$moduleBase/tcp-lb",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listTcpLb(rctx, cb) })
    )
    server.post(
      "$moduleBase/tcp-lb", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createTcpLb(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the socks5 server")
          .put("address", "the bind address")
          .put("backend", "used as the backend servers")
          .put("protocol", "the protocol used by tcp-lb")
          .put("acceptorLoopGroup", "the acceptor event loop")
          .put("workerLoopGroup", "the worker event loop")
          .put("inBufferSize", 16384)
          .put("outBufferSize", 16384)
          .putArray("listOfCertKey") { add("alias of the cert-key to be used") }
          .put("securityGroup", "alias of the security group, default: (allow-all)")
          .build(),
        "name", "address", "backend"
      )
    )
    server.put(
      "$moduleBase/tcp-lb/:tl", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> updateTcpLb(rctx, cb) }, ObjectBuilder()
          .put("inBufferSize", 16384)
          .put("outBufferSize", 16384)
          .putArray("listOfCertKey") { add("alias of the cert-key to be used") }
          .put("securityGroup", "alias of the security group")
          .build()
      )
    )
    server.del(
      "$moduleBase/tcp-lb/:tl",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteTcpLb(rctx, cb) })
    )
    // socks5-server
    server.get(
      "$moduleBase/socks5-server/:socks5/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getSocks5ServerDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/socks5-server/:socks5",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getSocks5Server(rctx, cb) })
    )
    server.get(
      "$moduleBase/socks5-server",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listSocks5Server(rctx, cb) })
    )
    server.post(
      "$moduleBase/socks5-server", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createSocks5Server(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the socks5 server")
          .put("address", "the bind address")
          .put("backend", "used as backend, the socks5 only supports servers added into this group")
          .put("acceptorLoopGroup", "the acceptor event loop")
          .put("workerLoopGroup", "the worker event loop")
          .put("inBufferSize", 16384)
          .put("outBufferSize", 16384)
          .put("securityGroup", "alias of the security group, default: (allow-all)")
          .put("allowNonBackend", false)
          .build(),
        "name", "address", "backend"
      )
    )
    server.put(
      "$moduleBase/socks5-server/:socks5", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> updateSocks5Server(rctx, cb) }, ObjectBuilder()
          .put("inBufferSize", 16384)
          .put("outBufferSize", 16384)
          .put("securityGroup", "alias of the security group")
          .put("allowNonBackend", false)
          .build()
      )
    )
    server.del(
      "$moduleBase/socks5-server/:socks5",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteSocks5Server(rctx, cb) })
    )
    // dns-server
    server.get(
      "$moduleBase/dns-server/:dns/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getDNSServerDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/dns-server/:dns",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getDNSServer(rctx, cb) })
    )
    server.get(
      "$moduleBase/dns-server",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listDNSServer(rctx, cb) })
    )
    server.post(
      "$moduleBase/dns-server", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createDNSServer(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the dns server")
          .put("address", "the bind address")
          .put("rrsets", "the servers to be resolved")
          .put("eventLoopGroup", "the event loop group to run the dns server")
          .put("ttl", 0)
          .put("securityGroup", "alias of the security group, default: (allow-all)")
          .build(),
        "name", "address", "backend"
      )
    )
    server.put(
      "$moduleBase/dns-server/:dns", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> updateDNSServer(rctx, cb) }, ObjectBuilder()
          .put("ttl", 0)
          .put("securityGroup", "alias of the security group")
          .build()
      )
    )
    server.del(
      "$moduleBase/dns-server/:dns",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteDNSServer(rctx, cb) })
    )
    // event-loop
    server.get(
      "$moduleBase/event-loop-group/:elg/event-loop/:el/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getEventLoop(rctx, cb) })
    )
    server.get(
      "$moduleBase/event-loop-group/:elg/event-loop/:el",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getEventLoop(rctx, cb) })
    )
    server.get(
      "$moduleBase/event-loop-group/:elg/event-loop",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listEventLoop(rctx, cb) })
    )
    server.post(
      "$moduleBase/event-loop-group/:elg/event-loop", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createEventLoop(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the event loop")
          .build(),
        "name"
      )
    )
    server.del(
      "$moduleBase/event-loop-group/:elg/event-loop/:el",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteEventLoop(rctx, cb) })
    )
    // event-loop-group
    server.get(
      "$moduleBase/event-loop-group/:elg/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getEventLoopGroupDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/event-loop-group/:elg",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getEventLoopGroup(rctx, cb) })
    )
    server.get(
      "$moduleBase/event-loop-group",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listEventLoopGroup(rctx, cb) })
    )
    server.post(
      "$moduleBase/event-loop-group", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createEventLoopGroup(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the event loop group")
          .build(),
        "name"
      )
    )
    server.del(
      "$moduleBase/event-loop-group/:elg",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteEventLoopGroup(rctx, cb) })
    )
    // server-group in upstream
    server.get(
      "$moduleBase/upstream/:ups/server-group/:sg/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getServerGroupInUpstreamDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/upstream/:ups/server-group/:sg",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getServerGroupInUpstream(rctx, cb) })
    )
    server.get(
      "$moduleBase/upstream/:ups/server-group",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listServerGroupInUpstream(rctx, cb) })
    )
    server.post(
      "$moduleBase/upstream/:ups/server-group", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createServerGroupInUpstream(rctx, cb) },
        ObjectBuilder()
          .put("name", "alias of the server group to be added")
          .put("weight", 10)
          .putInst("annotations", ObjectBuilder().put("key", "value").build())
          .build(),
        "name"
      )
    )
    server.put(
      "$moduleBase/upstream/:ups/server-group/:sg", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> updateServerGroupInUpstream(rctx, cb) },
        ObjectBuilder()
          .put("weight", 10)
          .putInst("annotations", ObjectBuilder().put("key", "value").build())
          .build()
      )
    )
    server.del(
      "$moduleBase/upstream/:ups/server-group/:sg",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteServerGroupInUpstream(rctx, cb) })
    )
    // upstream
    server.get(
      "$moduleBase/upstream/:ups/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getUpstreamDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/upstream/:ups",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getUpstream(rctx, cb) })
    )
    server.get(
      "$moduleBase/upstream",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listUpstream(rctx, cb) })
    )
    server.post(
      "$moduleBase/upstream", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createUpstream(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the upstream")
          .build(),
        "name"
      )
    )
    server.del(
      "$moduleBase/upstream/:ups",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteUpstream(rctx, cb) })
    )
    // server
    server.get(
      "$moduleBase/server-group/:sg/server/:svr/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getServer(rctx, cb) })
    )
    server.get(
      "$moduleBase/server-group/:sg/server/:svr",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getServer(rctx, cb) })
    )
    server.get(
      "$moduleBase/server-group/:sg/server",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listServer(rctx, cb) })
    )
    server.post(
      "$moduleBase/server-group/:sg/server", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createServer(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the server")
          .put("address", "remote address, host:port or ip:port")
          .put("weight", 10)
          .build(),
        "name", "address"
      )
    )
    server.put(
      "$moduleBase/server-group/:sg/server/:svr", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> updateServer(rctx, cb) }, ObjectBuilder()
          .put("weight", 10)
          .build()
      )
    )
    server.del(
      "$moduleBase/server-group/:sg/server/:svr",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteServer(rctx, cb) })
    )
    // server-group
    server.get(
      "$moduleBase/server-group/:sg/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getServerGroupDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/server-group/:sg",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getServerGroup(rctx, cb) })
    )
    server.get(
      "$moduleBase/server-group",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listServerGroup(rctx, cb) })
    )
    server.post(
      "$moduleBase/server-group", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createServerGroup(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the server-group")
          .put("timeout", 1000)
          .put("period", 5000)
          .put("up", 2)
          .put("down", 3)
          .put("protocol", "the protocol used to do health check")
          .put("method", "load balancing method")
          .putInst("annotations", ObjectBuilder().put("key", "value").build())
          .put(
            "eventLoopGroup",
            "choose a event-loop-group for the server group. health check operations will be performed on the event loop group"
          )
          .build(),
        "name", "timeout", "period", "up", "down"
      )
    )
    server.put(
      "$moduleBase/server-group/:sg", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> updateServerGroup(rctx, cb) }, ObjectBuilder()
          .put("timeout", 1000)
          .put("period", 5000)
          .put("up", 2)
          .put("down", 3)
          .put("protocol", "the protocol used to do health check")
          .put("method", "load balancing method")
          .putInst("annotations", ObjectBuilder().put("key", "value").build())
          .build()
      )
    )
    server.del(
      "$moduleBase/server-group/:sg",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteServerGroup(rctx, cb) })
    )
    // security-group-rule
    server.get(
      "$moduleBase/security-group/:secg/security-group-rule/:secgr/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getSecurityGroupRule(rctx, cb) })
    )
    server.get(
      "$moduleBase/security-group/:secg/security-group-rule/:secgr",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getSecurityGroupRule(rctx, cb) })
    )
    server.get(
      "$moduleBase/security-group/:secg/security-group-rule",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listSecurityGroupRule(rctx, cb) })
    )
    server.post(
      "$moduleBase/security-group/:secg/security-group-rule", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createSecurityGroupRule(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the security group rule")
          .put("clientNetwork", "a cidr string for checking client ip")
          .put("protocol", "protocol of the rule")
          .put("serverPortMin", 0)
          .put("serverPortMax", 65536)
          .put("rule", "allow or deny the request")
          .build(),
        "name", "clientNetwork", "protocol", "serverPortMin", "serverPortMax", "rule"
      )
    )
    server.del(
      "$moduleBase/security-group/:secg/security-group-rule/:secgr",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteSecurityGroupRule(rctx, cb) })
    )
    // security-group
    server.get(
      "$moduleBase/security-group/:secg/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getSecurityGroupDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/security-group/:secg",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getSecurityGroup(rctx, cb) })
    )
    server.get(
      "$moduleBase/security-group",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listSecurityGroup(rctx, cb) })
    )
    server.post(
      "$moduleBase/security-group", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createSecurityGroup(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the security group")
          .put("defaultRule", "allow or deny access if no match in the rule list")
          .build(),
        "name", "defaultRule"
      )
    )
    server.put(
      "$moduleBase/security-group/:secg", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> updateSecurityGroup(rctx, cb) }, ObjectBuilder()
          .put("defaultRule", "allow or deny access if no match in the rule list")
          .build()
      )
    )
    server.del(
      "$moduleBase/security-group/:secg",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteSecurityGroup(rctx, cb) })
    )
    // cert-key
    server.get(
      "$moduleBase/cert-key/:ck/detail",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getCertKeyDetail(rctx, cb) })
    )
    server.get(
      "$moduleBase/cert-key/:ck",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getCertKey(rctx, cb) })
    )
    server.get(
      "$moduleBase/cert-key",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listCertKey(rctx, cb) })
    )
    server.post(
      "$moduleBase/cert-key", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> createCertKey(rctx, cb) }, ObjectBuilder()
          .put("name", "alias of the cert-key")
          .putArray("certs") { add("path to certificate pem file") }
          .put("key", "path to private key pem file")
          .build(),
        "name", "certs", "key"
      )
    )
    server.post(
      "$moduleBase/cert-key/pem", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> uploadCertKey(rctx, cb) }, ObjectBuilder()
          .put("name", "")
          .putArray("certs") { add("pem of certificate to upload") }
          .put("key", "pem of key to upload")
          .build(),
        "name", "certs", "key"
      )
    )
    server.del(
      "$moduleBase/cert-key/:ck",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteCertKey(rctx, cb) })
    )
    // server-sock
    server.get(
      "$channelBase/event-loop-groups/:elgs/event-loop/:el/server-sock",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listServerSocksInEl(rctx, cb) })
    )
    server.get(
      "$channelBase/tcp-lb/:tl/server-sock",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listServerSocksInTl(rctx, cb) })
    )
    server.get(
      "$channelBase/socks5-server/:socks5/server-sock",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listServerSocksInSocks5(rctx, cb) })
    )
    // connection
    server.get(
      "$channelBase/event-loop-groups/:elgs/event-loop/:el/conn",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listConnInEl(rctx, cb) })
    )
    server.get(
      "$channelBase/tcp-lb/:tl/conn",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listConnInTl(rctx, cb) })
    )
    server.get(
      "$channelBase/socks5-server/:socks5/conn",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listConnInSocks5(rctx, cb) })
    )
    server.get(
      "$channelBase/server-group/:sg/server/:svr/conn",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listConnInServer(rctx, cb) })
    )
    server.del(
      "$channelBase/event-loop-groups/:elgs/event-loop/:el/conn/:l4addr-act/:l4addr-pas", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteConnFromEl(rctx, cb) })
    )
    server.del(
      "$channelBase/tcp-lb/:tl/conn/:l4addr-act/:l4addr-pas",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteConnFromTl(rctx, cb) })
    )
    server.del(
      "$channelBase/socks5-server/:socks5/conn/:l4addr-act/:l4addr-pas",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteConnFromSocks5(rctx, cb) })
    )
    server.del(
      "$channelBase/server-group/:sg/server/:svr/conn/:l4addr-act/:l4addr-pas",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteConnFromServer(rctx, cb) })
    )
    server.del(
      "$channelBase/event-loop-groups/:elgs/event-loop/:el/conn/:regexp",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteConnFromElRegexp(rctx, cb) })
    )
    server.del(
      "$channelBase/tcp-lb/:tl/conn/:regexp",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteConnFromTlRegexp(rctx, cb) })
    )
    server.del(
      "$channelBase/socks5-server/:socks5/conn/:regexp",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteConnFromSocks5Regexp(rctx, cb) })
    )
    server.del(
      "$channelBase/server-group/:sg/server/:svr/conn/:regexp",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteConnFromServerRegexp(rctx, cb) })
    )
    // session
    server.get(
      "$channelBase/tcp-lb/:tl/session",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listSessionInTl(rctx, cb) })
    )
    server.get(
      "$channelBase/socks5-server/:socks5/session",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listSessionInSocks5(rctx, cb) })
    )
    server.del(
      "$channelBase/tcp-lb/:tl/session/:front-act/:front-pas/:back-act/:back-pas",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteSessionInTl(rctx, cb) })
    )
    server.del(
      "$channelBase/socks5-server/:socks5/session/:front-act/:front-pas/:back-act/:back-pas", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteSessionInSocks5(rctx, cb) })
    )
    server.del(
      "$channelBase/tcp-lb/:tl/session/:regexp",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteSessionInTlRegexp(rctx, cb) })
    )
    server.del(
      "$channelBase/socks5-server/:socks5/session/:regexp",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> deleteSessionInSocks5Regexp(rctx, cb) })
    )
    // dns-cache
    server.get(
      "$stateBase/dns-cache",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> listDnsCache(rctx, cb) })
    )
    // bytes-in
    server.get(
      "$statistics/tcp-lb/:tl/server-sock/:l4addr/bytes-in",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesInFromL4AddrTl(rctx, cb) })
    )
    server.get(
      "$statistics/socks5-server/:socks5/server-sock/:l4addr/bytes-in",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesInFromL4AddrSocks5(rctx, cb) })
    )
    server.get(
      "$statistics/event-loop-group/:elg/event-loop/:el/conn/:l4addr-act/:l4addr-pas/bytes-in", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesInFromConnectionOfEl(rctx, cb) })
    )
    server.get(
      "$statistics/tcp-lb/:tl/conn/:l4addr-act/:l4addr-pas/bytes-in",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesInFromConnectionOfTl(rctx, cb) })
    )
    server.get(
      "$statistics/socks5-server/:socks5/conn/:l4addr-act/:l4addr-pas/bytes-in",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesInFromConnectionOfSocks5(rctx, cb) })
    )
    server.get(
      "$statistics/server-group/:sg/server/:svr/conn/:l4addr-act/:l4addr-pas/bytes-in", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesInFromConnectionOfServer(rctx, cb) })
    )
    server.get(
      "$statistics/server-group/:sg/server/:svr/bytes-in",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesInFromServer(rctx, cb) })
    )
    // bytes-out
    server.get(
      "$statistics/tcp-lb/:tl/server-sock/:l4addr/bytes-out",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesOutFromL4AddrTl(rctx, cb) })
    )
    server.get(
      "$statistics/socks5-server/:socks5/server-sock/:l4addr/bytes-out",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesOutFromL4AddrSocks5(rctx, cb) })
    )
    server.get(
      "$statistics/event-loop-group/:elg/event-loop/:el/conn/:l4addr-act/:l4addr-pas/bytes-out", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesOutFromConnectionOfEl(rctx, cb) })
    )
    server.get(
      "$statistics/tcp-lb/:tl/conn/:l4addr-act/:l4addr-pas/bytes-out",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesOutFromConnectionOfTl(rctx, cb) })
    )
    server.get(
      "$statistics/socks5-server/:socks5/conn/:l4addr-act/:l4addr-pas/bytes-out",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesOutFromConnectionOfSocks5(rctx, cb) })
    )
    server.get(
      "$statistics/server-group/:sg/server/:svr/conn/:l4addr-act/:l4addr-pas/bytes-out", wrapAsync(
        { rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesOutFromConnectionOfServer(rctx, cb) })
    )
    server.get(
      "$statistics/server-group/:sg/server/:svr/bytes-out",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getBytesOutFromServer(rctx, cb) })
    )
    // accepted-conn-count
    server.get(
      "$statistics/tcp-lb/:tl/server-sock/:l4addr/accepted-conn",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getAcceptedConnFromL4AddrTl(rctx, cb) })
    )
    server.get(
      "$statistics/socks5-server/:socks5/server-sock/:l4addr/accepted-conn",
      wrapAsync({ rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable> -> getAcceptedConnFromL4AddrSocks5(rctx, cb) })
    )
    // watch
    server.get("$watch/server-group/-/server/-/health-check") { rctx: RoutingContext -> watchHealthCheck(rctx) }

    // start
    loop.selectorEventLoop.launch {
      server.start()
    }
  }

  fun stop() {
    server.close()
  }

  @Suppress("unused_parameter")
  private fun getAll(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    cb.succeeded(utils.all())
  }

  private fun getTcpLbDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    cb.succeeded(utils.formatTcpLbDetail(tl))
  }

  private fun getTcpLb(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    cb.succeeded(utils.formatTcpLb(tl))
  }

  @Suppress("unused_parameter")
  private fun listTcpLb(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val holder = Application.get().tcpLBHolder
    val names: List<String> = holder.names()
    val arr = ArrayBuilder()
    for (name in names) {
      val tl: TcpLB = holder.get(name)
      arr.addInst(utils.formatTcpLb(tl))
    }
    cb.succeeded(arr.build())
  }

  private fun bodyContainsKey(body: JSON.Object, key: String): Boolean {
    return if (!body.containsKey(key)) {
      false
    } else body[key] !is JSON.Null
  }

  private fun createTcpLb(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val name = body.getString("name")
    val address = body.getString("address")
    val backend = body.getString("backend")
    val options = LinkedList(listOf("add", "tcp-lb", name, "address", address, "upstream", backend))
    if (bodyContainsKey(body, "protocol")) {
      options.add("protocol")
      options.add(body.getString("protocol"))
    }
    if (bodyContainsKey(body, "acceptorLoopGroup")) {
      options.add("acceptor-elg")
      options.add(body.getString("acceptorLoopGroup"))
    }
    if (bodyContainsKey(body, "workerLoopGroup")) {
      options.add("event-loop-group")
      options.add(body.getString("workerLoopGroup"))
    }
    if (bodyContainsKey(body, "inBufferSize")) {
      options.add("in-buffer-size")
      options.add("" + body.getInt("inBufferSize"))
    }
    if (bodyContainsKey(body, "outBufferSize")) {
      options.add("out-buffer-size")
      options.add("" + body.getInt("outBufferSize"))
    }
    if (bodyContainsKey(body, "listOfCertKey")) {
      val arr = body.getArray("listOfCertKey")
      if (arr.length() > 0) {
        options.add("cert-key")
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
          if (i != 0) {
            sb.append(",")
          }
          sb.append(arr.getString(i))
        }
        options.add(sb.toString())
      }
    }
    if (bodyContainsKey(body, "securityGroup")) {
      options.add("security-group")
      options.add(body.getString("securityGroup"))
    }
    utils.execute(cb, options)
  }

  private fun updateTcpLb(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val options = LinkedList(listOf("update", "tcp-lb", rctx.param("tl")))
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    if (bodyContainsKey(body, "inBufferSize")) {
      options.add("in-buffer-size")
      options.add("" + body.getInt("inBufferSize"))
    }
    if (bodyContainsKey(body, "outBufferSize")) {
      options.add("out-buffer-size")
      options.add("" + body.getInt("outBufferSize"))
    }
    if (bodyContainsKey(body, "listOfCertKey")) {
      val arr = body.getArray("listOfCertKey")
      if (arr.length() > 0) {
        options.add("cert-key")
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
          if (i != 0) {
            sb.append(",")
          }
          sb.append(arr.getString(i))
        }
        options.add(sb.toString())
      } else {
        // additional check
        val tl: TcpLB
        try {
          tl = Application.get().tcpLBHolder[rctx.param("tl")]
        } catch (e: NotFoundException) {
          cb.failed(e)
          return
        }
        if (tl.certKeys != null && tl.certKeys.isNotEmpty()) {
          cb.failed(Err(400, "cannot configure the tcp-lb to use plain TCP when it's originally using TLS"))
          return
        }
      }
    }
    if (bodyContainsKey(body, "securityGroup")) {
      options.add("security-group")
      options.add(body.getString("securityGroup"))
    }
    utils.execute(cb, options)
  }

  private fun deleteTcpLb(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb,
      "remove", "tcp-lb", rctx.param("tl")
    )
  }

  private fun getSocks5ServerDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val s = Application.get().socks5ServerHolder[rctx.param("socks5")]
    cb.succeeded(utils.formatSocks5ServerDetail(s))
  }

  private fun getSocks5Server(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val s = Application.get().socks5ServerHolder[rctx.param("socks5")]
    cb.succeeded(utils.formatSocks5Server(s))
  }

  @Suppress("unused_parameter")
  private fun listSocks5Server(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val holder = Application.get().socks5ServerHolder
    val names: List<String> = holder.names()
    val arr = ArrayBuilder()
    for (name in names) {
      val s: Socks5Server = holder.get(name)
      arr.addInst(utils.formatSocks5Server(s))
    }
    cb.succeeded(arr.build())
  }

  private fun createSocks5Server(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val name = body.getString("name")
    val address = body.getString("address")
    val backend = body.getString("backend")
    val options = LinkedList(listOf("add", "socks5-server", name, "address", address, "upstream", backend))
    if (bodyContainsKey(body, "acceptorLoopGroup")) {
      options.add("acceptor-elg")
      options.add(body.getString("acceptorLoopGroup"))
    }
    if (bodyContainsKey(body, "workerLoopGroup")) {
      options.add("event-loop-group")
      options.add(body.getString("workerLoopGroup"))
    }
    if (bodyContainsKey(body, "inBufferSize")) {
      options.add("in-buffer-size")
      options.add("" + body.getInt("inBufferSize"))
    }
    if (bodyContainsKey(body, "outBufferSize")) {
      options.add("out-buffer-size")
      options.add("" + body.getInt("outBufferSize"))
    }
    if (bodyContainsKey(body, "securityGroup")) {
      options.add("security-group")
      options.add(body.getString("securityGroup"))
    }
    if (bodyContainsKey(body, "allowNonBackend")) {
      val allowNonBackend = body.getBool("allowNonBackend")
      if (allowNonBackend) {
        options.add("allow-non-backend")
      } else {
        options.add("deny-non-backend")
      }
    }
    utils.execute(cb, options)
  }

  private fun updateSocks5Server(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val options = LinkedList(listOf("update", "socks5-server", rctx.param("socks5")))
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    if (bodyContainsKey(body, "inBufferSize")) {
      options.add("in-buffer-size")
      options.add("" + body.getInt("inBufferSize"))
    }
    if (bodyContainsKey(body, "outBufferSize")) {
      options.add("out-buffer-size")
      options.add("" + body.getInt("outBufferSize"))
    }
    if (bodyContainsKey(body, "securityGroup")) {
      options.add("security-group")
      options.add(body.getString("securityGroup"))
    }
    if (bodyContainsKey(body, "allowNonBackend")) {
      val allowNonBackend = body.getBool("allowNonBackend")
      if (allowNonBackend) {
        options.add("allow-non-backend")
      } else {
        options.add("deny-non-backend")
      }
    }
    utils.execute(cb, options)
  }

  private fun deleteSocks5Server(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(cb, "remove", "socks5-server", rctx.param("socks5"))
  }

  private fun getDNSServerDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val dns = Application.get().dnsServerHolder[rctx.param("dns")]
    cb.succeeded(utils.formatDNSServerDetail(dns))
  }

  private fun getDNSServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val dns = Application.get().dnsServerHolder[rctx.param("dns")]
    cb.succeeded(utils.formatDNSServer(dns))
  }

  @Suppress("unused_parameter")
  private fun listDNSServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val holder = Application.get().dnsServerHolder
    val names: List<String> = holder.names()
    val arr = ArrayBuilder()
    for (name in names) {
      val d: DNSServer = holder.get(name)
      arr.addInst(utils.formatDNSServer(d))
    }
    cb.succeeded(arr.build())
  }

  private fun createDNSServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val name = body.getString("name")
    val address = body.getString("address")
    val rrsets = body.getString("rrsets")
    val options = LinkedList(listOf("add", "dns-server", name, "address", address, "upstream", rrsets))
    if (bodyContainsKey(body, "eventLoopGroup")) {
      options.add("event-loop-group")
      options.add(body.getString("eventLoopGroup"))
    }
    if (bodyContainsKey(body, "ttl")) {
      options.add("ttl")
      options.add("" + body.getInt("ttl"))
    }
    if (bodyContainsKey(body, "securityGroup")) {
      options.add("security-group")
      options.add(body.getString("securityGroup"))
    }
    utils.execute(cb, options)
  }

  private fun updateDNSServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val options = LinkedList(listOf("update", "dns-server", rctx.param("dns")))
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    if (bodyContainsKey(body, "ttl")) {
      options.add("ttl")
      options.add("" + body.getInt("ttl"))
    }
    if (bodyContainsKey(body, "securityGroup")) {
      options.add("security-group")
      options.add(body.getString("securityGroup"))
    }
    utils.execute(cb, options)
  }

  private fun deleteDNSServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "dns-server", rctx.param("dns")
    )
  }

  private fun getEventLoop(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val el = Application.get().eventLoopGroupHolder[rctx.param("elg")][rctx.param("el")]
    cb.succeeded(utils.formatEventLoop(el))
  }

  private fun listEventLoop(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val elg = Application.get().eventLoopGroupHolder[rctx.param("elg")]
    val list = elg.list()
    val arr = ArrayBuilder()
    list.forEach { el -> arr.addInst(utils.formatEventLoop(el)) }
    cb.succeeded(arr.build())
  }

  private fun createEventLoop(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val name = body.getString("name")
    utils.execute(
      cb,
      "add", "event-loop", name, "to", "event-loop-group", rctx.param("elg")
    )
  }

  private fun deleteEventLoop(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "event-loop", rctx.param("el"), "from", "event-loop-group", rctx.param("elg")
    )
  }

  private fun getEventLoopGroupDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val elg = Application.get().eventLoopGroupHolder[rctx.param("elg")]
    cb.succeeded(utils.formatEventLoopGroupDetail(elg))
  }

  private fun getEventLoopGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val elg = Application.get().eventLoopGroupHolder[rctx.param("elg")]
    cb.succeeded(utils.formatEventLoopGroup(elg))
  }

  @Suppress("unused_parameter")
  private fun listEventLoopGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val arr = ArrayBuilder()
    val holder = Application.get().eventLoopGroupHolder
    val names: List<String> = holder.names()
    for (name in names) {
      val elg: EventLoopGroup = holder.get(name)
      arr.addInst(utils.formatEventLoopGroup(elg))
    }
    cb.succeeded(arr.build())
  }

  private fun createEventLoopGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val name = body.getString("name")
    utils.execute(
      cb,
      "add", "event-loop-group", name
    )
  }

  private fun deleteEventLoopGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(cb, "remove", "event-loop-group", rctx.param("elg"))
  }

  private fun getServerGroupInUpstreamDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val upsName = rctx.param("ups")
    val ups = Application.get().upstreamHolder.get(upsName)
    val sgName = rctx.param("sg")
    val opt = ups.serverGroupHandles.stream().filter { sg: Upstream.ServerGroupHandle -> sg.alias == sgName }
      .findAny()
    if (opt.isEmpty) {
      throw NotFoundException("server-group in upstream $upsName", sgName)
    } else {
      cb.succeeded(utils.formatServerGroupInUpstreamDetail(opt.get()))
    }
  }

  private fun getServerGroupInUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val upsName = rctx.param("ups")
    val ups = Application.get().upstreamHolder.get(upsName)
    val sgName = rctx.param("sg")
    val opt = ups.serverGroupHandles.stream().filter { sg: Upstream.ServerGroupHandle -> sg.alias == sgName }
      .findAny()
    if (opt.isEmpty) {
      throw NotFoundException("server-group in upstream $upsName", sgName)
    } else {
      cb.succeeded(utils.formatServerGroupInUpstream(opt.get()))
    }
  }

  private fun listServerGroupInUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val ups = Application.get().upstreamHolder[rctx.param("ups")]
    val arr = ArrayBuilder()
    ups.serverGroupHandles.forEach(Consumer { sg: Upstream.ServerGroupHandle? -> arr.addInst(utils.formatServerGroupInUpstream(sg)) })
    cb.succeeded(arr.build())
  }

  private fun createServerGroupInUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val ups = rctx.param("ups")
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val sg = body.getString("name")
    val options = LinkedList(listOf("add", "server-group", sg, "to", "upstream", ups))
    if (bodyContainsKey(body, "weight")) {
      options.add("weight")
      options.add("" + body.getInt("weight"))
    }
    if (bodyContainsKey(body, "annotations")) {
      options.add("annotations")
      options.add(body.getObject("annotations").stringify())
    }
    utils.execute(cb, options)
  }

  private fun updateServerGroupInUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val ups = rctx.param("ups")
    val sg = rctx.param("sg")
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val options = LinkedList(listOf("update", "server-group", sg, "in", "upstream", ups))
    if (bodyContainsKey(body, "weight")) {
      options.add("weight")
      options.add("" + body.getInt("weight"))
    }
    if (bodyContainsKey(body, "annotations")) {
      options.add("annotations")
      options.add(body.getObject("annotations").stringify())
    }
    utils.execute(cb, options)
  }

  private fun deleteServerGroupInUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val ups = rctx.param("ups")
    val sg = rctx.param("sg")
    utils.execute(
      cb, "remove", "server-group", sg, "from", "upstream", ups
    )
  }

  private fun getUpstreamDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val ups = Application.get().upstreamHolder[rctx.param("ups")]
    cb.succeeded(utils.formatUpstreamDetail(ups))
  }

  private fun getUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val ups = Application.get().upstreamHolder[rctx.param("ups")]
    cb.succeeded(utils.formatUpstream(ups))
  }

  @Suppress("unused_parameter")
  private fun listUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val holder = Application.get().upstreamHolder
    val names: List<String> = holder.names()
    val arr = ArrayBuilder()
    for (name in names) {
      val ups: Upstream = holder.get(name)
      arr.addInst(utils.formatUpstream(ups))
    }
    cb.succeeded(arr.build())
  }

  private fun createUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    utils.execute(
      cb, "add", "upstream", body.getString("name")
    )
  }

  private fun deleteUpstream(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "upstream", rctx.param("ups")
    )
  }

  @Throws(NotFoundException::class)
  private fun getServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val sgName = rctx.param("sg")
    val sg = Application.get().serverGroupHolder.get(sgName)
    val alias = rctx.param("svr")
    val opt = sg.serverHandles.stream().filter { h: ServerGroup.ServerHandle -> h.alias == alias }.findAny()
    if (opt.isEmpty) {
      throw NotFoundException("server in server-group $sgName", alias)
    } else {
      cb.succeeded(utils.formatServer(opt.get()))
    }
  }

  @Throws(NotFoundException::class)
  private fun listServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val sg = Application.get().serverGroupHolder[rctx.param("sg")]
    val arr = ArrayBuilder()
    sg.serverHandles.forEach(Consumer { h: ServerGroup.ServerHandle? -> arr.addInst(utils.formatServer(h)) })
    cb.succeeded(arr.build())
  }

  private fun createServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val options = LinkedList(
      listOf(
        "add", "server", body.getString("name"), "to", "server-group", rctx.param("sg"),
        "address", body.getString("address")
      )
    )
    if (bodyContainsKey(body, "weight")) {
      options.add("weight")
      options.add("" + body.getInt("weight"))
    }
    utils.execute(cb, options)
  }

  private fun updateServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val options: MutableList<String> = LinkedList<String>()
    options.add("update")
    options.add("server")
    options.add(rctx.param("svr"))
    options.add("in")
    options.add("server-group")
    options.add(rctx.param("sg"))
    if (bodyContainsKey(body, "weight")) {
      options.add("weight")
      options.add("" + body.getInt("weight"))
    }
    utils.execute(cb, options)
  }

  private fun deleteServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb,
      "remove", "server", rctx.param("svr"), "from", "server-group", rctx.param("sg")
    )
  }

  private fun getServerGroupDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val sg = Application.get().serverGroupHolder[rctx.param("sg")]
    cb.succeeded(utils.formatServerGroupDetail(sg))
  }

  private fun getServerGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val sg = Application.get().serverGroupHolder[rctx.param("sg")]
    cb.succeeded(utils.formatServerGroup(sg))
  }

  @Suppress("unused_parameter")
  private fun listServerGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val holder = Application.get().serverGroupHolder
    val names: List<String> = holder.names()
    val arr = ArrayBuilder()
    for (name in names) {
      arr.addInst(utils.formatServerGroup(holder.get(name)))
    }
    cb.succeeded(arr.build())
  }

  private fun createServerGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val options: MutableList<String> = LinkedList<String>()
    options.add("add")
    options.add("server-group")
    options.add(body.getString("name"))
    options.add("timeout")
    options.add("" + body.getInt("timeout"))
    options.add("period")
    options.add("" + body.getInt("period"))
    options.add("up")
    options.add("" + body.getInt("up"))
    options.add("down")
    options.add("" + body.getInt("down"))
    if (bodyContainsKey(body, "protocol")) {
      options.add("protocol")
      options.add(body.getString("protocol"))
    }
    if (bodyContainsKey(body, "method")) {
      options.add("method")
      options.add(body.getString("method"))
    }
    if (bodyContainsKey(body, "eventLoopGroup")) {
      options.add("event-loop-group")
      options.add(body.getString("eventLoopGroup"))
    }
    if (bodyContainsKey(body, "annotations")) {
      options.add("annotations")
      options.add(body.getObject("annotations").stringify())
    }
    utils.execute(cb, options)
  }

  private fun updateServerGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val options: MutableList<String> = LinkedList<String>()
    options.add("update")
    options.add("server-group")
    options.add(rctx.param("sg"))
    if (bodyContainsKey(body, "timeout") || bodyContainsKey(body, "period") || bodyContainsKey(body, "up") || bodyContainsKey(
        body,
        "down"
      ) || bodyContainsKey(body, "protocol")
    ) {
      // health check options should be set all together
      if (!bodyContainsKey(body, "timeout")
        || !bodyContainsKey(body, "period")
        || !bodyContainsKey(body, "up")
        || !bodyContainsKey(body, "down")
      ) {
        cb.failed(Err(400, "health check options should be set together"))
        return
      }
      val timeout = body.getInt("timeout")
      val period = body.getInt("period")
      val up = body.getInt("up")
      val down = body.getInt("down")
      options.add("timeout")
      options.add("" + timeout)
      options.add("period")
      options.add("" + period)
      options.add("up")
      options.add("" + up)
      options.add("down")
      options.add("" + down)
      if (bodyContainsKey(body, "protocol")) {
        options.add("protocol")
        options.add(body.getString("protocol"))
      }
    }
    if (bodyContainsKey(body, "method")) {
      options.add("method")
      options.add(body.getString("method"))
    }
    if (bodyContainsKey(body, "annotations")) {
      options.add("annotations")
      options.add(body.getObject("annotations").stringify())
    }
    utils.execute(cb, options)
  }

  private fun deleteServerGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb,
      "remove", "server-group", rctx.param("sg")
    )
  }

  private fun getSecurityGroupRule(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val secgName = rctx.param("secg")
    val secg = Application.get().securityGroupHolder.get(secgName)
    val rName = rctx.param("secgr")
    val opt = secg.rules.stream().filter { r: SecurityGroupRule -> r.alias == rName }.findAny()
    if (opt.isEmpty) {
      throw NotFoundException("security-group-rule in security-group $secgName", rName)
    } else {
      cb.succeeded(utils.formatSecurityGroupRule(opt.get()))
    }
  }

  private fun listSecurityGroupRule(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val secg = Application.get().securityGroupHolder[rctx.param("secg")]
    val rules = secg.rules
    val arr = ArrayBuilder()
    for (rule in rules) {
      arr.addInst(utils.formatSecurityGroupRule(rule))
    }
    cb.succeeded(arr.build())
  }

  private fun createSecurityGroupRule(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val secg = rctx.param("secg")
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    utils.execute(
      cb,
      "add", "security-group-rule", body.getString("name"), "to", "security-group", secg,
      "network", body.getString("clientNetwork"),
      "protocol", body.getString("protocol"),
      "port-range", body.getInt("serverPortMin").toString() + "," + body.getInt("serverPortMax"),
      "default", body.getString("rule")
    )
  }

  private fun deleteSecurityGroupRule(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val secgr = rctx.param("secgr")
    val secg = rctx.param("secg")
    utils.execute(
      cb, "remove", "security-group-rule", secgr, "from", "security-group", secg
    )
  }

  private fun getSecurityGroupDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val secg = Application.get().securityGroupHolder[rctx.param("secg")]
    cb.succeeded(utils.formatSecurityGroupDetail(secg))
  }

  private fun getSecurityGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val secg = Application.get().securityGroupHolder[rctx.param("secg")]
    cb.succeeded(utils.formatSecurityGroup(secg))
  }

  @Suppress("unused_parameter")
  private fun listSecurityGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val holder = Application.get().securityGroupHolder
    val names: List<String> = holder.names()
    val arr = ArrayBuilder()
    for (name in names) {
      arr.addInst(utils.formatSecurityGroup(holder.get(name)))
    }
    cb.succeeded(arr.build())
  }

  private fun createSecurityGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    utils.execute(
      cb,
      "add", "security-group", body.getString("name"), "default", body.getString("defaultRule")
    )
  }

  private fun updateSecurityGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val options = LinkedList(listOf("update", "security-group", rctx.param("secg")))
    if (bodyContainsKey(body, "defaultRule")) {
      options.add("default")
      options.add(body.getString("defaultRule"))
    }
    utils.execute(cb, options)
  }

  private fun deleteSecurityGroup(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "security-group", rctx.param("secg")
    )
  }

  private fun getCertKeyDetail(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val ck = Application.get().certKeyHolder[rctx.param("ck")]
    cb.succeeded(utils.formatCertKeyDetail(ck))
  }

  private fun getCertKey(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val ck = Application.get().certKeyHolder[rctx.param("ck")]
    cb.succeeded(utils.formatCertKey(ck))
  }

  @Suppress("unused_parameter")
  private fun listCertKey(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val holder = Application.get().certKeyHolder
    val names: List<String> = holder.names()
    val arr = ArrayBuilder()
    for (name in names) {
      val ck: CertKey = holder.get(name)
      arr.addInst(utils.formatCertKey(ck))
    }
    cb.succeeded(arr.build())
  }

  private fun createCertKey(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val name = body.getString("name")
    val certs = body.getArray("certs")
    val key = body.getString("key")
    val cert = StringBuilder()
    for (i in 0 until certs.length()) {
      if (i != 0) {
        cert.append(",")
      }
      cert.append(certs[i].toJavaObject())
    }
    utils.execute(
      cb,
      "add", "cert-key", name, "cert", cert.toString(), "key", key
    )
  }

  private fun uploadCertKey(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val body = rctx.get(Tool.bodyJson) as JSON.Object
    val bodyCerts = body.getArray("certs")
    // validate the cert key
    val certs = arrayOfNulls<String>(body.getArray("certs").length())
    for (i in 0 until bodyCerts.length()) {
      certs[i] = bodyCerts.getString(i)
    }
    val key: String = body.getString("key")

    // validate
    val ck = CertKey("tmp", certs, key)
    try {
      ck.validate()
    } catch (e: Exception) {
      throw Err(400, "invalid certs or key or invalid combination of certs and key")
    }

    // save the files
    val certFileNames = arrayOfNulls<String>(certs.size)
    val keyFileName: String
    try {
      for (i in certs.indices) {
        certFileNames[i] = utils.savePem(certs[i])
      }
      keyFileName = utils.savePem(key)
    } catch (e: XException) {
      throw Err(500, e.message)
    }

    // prepare to call commands
    val name = body.getString("name")
    val certFileNamesStr = StringBuilder()
    for (i in certFileNames.indices) {
      if (i != 0) {
        certFileNamesStr.append(",")
      }
      certFileNamesStr.append(certFileNames[i])
    }
    utils.execute(
      cb,
      "add", "cert-key", name, "cert", certFileNamesStr.toString(), "key", keyFileName
    )
  }

  private fun deleteCertKey(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb,
      "remove", "cert-key", rctx.param("ck")
    )
  }

  @Throws(NotFoundException::class)
  private fun listServerSocksInEl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val el = utils.getEventLoop(rctx)
    val servers = LinkedList<ServerSock>()
    el.copyServers(servers)
    utils.respondServerSockList(servers, cb)
  }

  @Throws(NotFoundException::class)
  private fun listServerSocksInTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    utils.respondServerSockListInTl(tl, cb)
  }

  @Throws(NotFoundException::class)
  private fun listServerSocksInSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val socks5 = Application.get().socks5ServerHolder[rctx.param("socks5")]
    utils.respondServerSockListInTl(socks5, cb)
  }

  @Throws(NotFoundException::class)
  private fun listConnInEl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val conns = utils.listConnectionFromEl(rctx)
    utils.respondConnectionList(conns, cb)
  }

  @Throws(NotFoundException::class)
  private fun listConnInTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    val conns = utils.listConnectionFromTl(tl)
    utils.respondConnectionList(conns, cb)
  }

  @Throws(NotFoundException::class)
  private fun listConnInSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val socks5 = Application.get().socks5ServerHolder[rctx.param("socks5")]
    val conns = utils.listConnectionFromTl(socks5)
    utils.respondConnectionList(conns, cb)
  }

  @Throws(NotFoundException::class)
  private fun listConnInServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val conns = utils.listConnectionFromServer(rctx)
    utils.respondConnectionList(conns, cb)
  }

  private fun deleteConnFromEl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "connection",
      rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas"),
      "in", "event-loop", rctx.param("el"), "in", "event-loop-group", rctx.param("elg")
    )
  }

  private fun deleteConnFromTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "connection",
      rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas"),
      "in", "tcp-lb", rctx.param("tl")
    )
  }

  private fun deleteConnFromSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "connection",
      rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas"),
      "in", "socks5-server", rctx.param("socks5")
    )
  }

  private fun deleteConnFromServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "connection",
      rctx.param("l4addr-act") + "/" + rctx.param("l4addr-pas"),
      "in", "server", rctx.param("svr"), "in", "server-group", rctx.param("sg")
    )
  }

  private fun deleteConnFromElRegexp(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "connection", "/" + rctx.param("regexp") + "/",
      "in", "event-loop", rctx.param("el"), "in", "event-loop-group", rctx.param("elg")
    )
  }

  private fun deleteConnFromTlRegexp(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "connection", "/" + rctx.param("regexp") + "/",
      "in", "tcp-lb", rctx.param("tl")
    )
  }

  private fun deleteConnFromSocks5Regexp(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "connection", "/" + rctx.param("regexp") + "/",
      "in", "socks5-server", rctx.param("socks5")
    )
  }

  private fun deleteConnFromServerRegexp(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "connection", "/" + rctx.param("regexp") + "/",
      "in", "server", rctx.param("svr"), "in", "server-group", rctx.param("sg")
    )
  }

  @Throws(NotFoundException::class)
  private fun listSessionInTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    utils.listSessionsInTl(tl, cb)
  }

  @Throws(NotFoundException::class)
  private fun listSessionInSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val socks5 = Application.get().socks5ServerHolder[rctx.param("socks5")]
    utils.listSessionsInTl(socks5, cb)
  }

  private fun deleteSessionInTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "session",
      rctx.param("front-act") + "/" + rctx.param("front-pas") + "->" + rctx.param("back-act") + "/" + rctx.param("back-pas"),
      "in", "tcp-lb", rctx.param("tl")
    )
  }

  private fun deleteSessionInSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "session",
      rctx.param("front-act") + "/" + rctx.param("front-pas") + "->" + rctx.param("back-act") + "/" + rctx.param("back-pas"),
      "in", "socks5-server", rctx.param("socks5")
    )
  }

  private fun deleteSessionInTlRegexp(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "session", "/" + rctx.param("regexp") + "/",
      "in", "tcp-lb", rctx.param("tl")
    )
  }

  private fun deleteSessionInSocks5Regexp(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    utils.execute(
      cb, "remove", "session", "/" + rctx.param("regexp") + "/",
      "in", "socks5-server", rctx.param("socks5")
    )
  }

  @Suppress("unused_parameter")
  private fun listDnsCache(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val list: LinkedList<Cache> = LinkedList<Cache>()
    Resolver.getDefault().copyCache(list)
    val ret: List<JSON.Object> = list.stream().map { c: Cache ->
      ObjectBuilder()
        .put("host", c.host)
        .putArray("ipv4") { c.ipv4.forEach { i -> add(i.formatToIPString()) } }
        .putArray("ipv6") { c.ipv6.forEach { i -> add(i.formatToIPString()) } }
        .put("timestamp", c.timestamp)
        .build()
    }.collect(Collectors.toList())
    cb.succeeded(SimpleArray(ret))
  }

  @Throws(NotFoundException::class)
  private fun getBytesInFromL4AddrTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    utils.respondBytesInFromL4AddrTl(rctx.param("l4addr"), tl, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesInFromL4AddrSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val socks5 = Application.get().socks5ServerHolder[rctx.param("socks5")]
    utils.respondBytesInFromL4AddrTl(rctx.param("l4addr"), socks5, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesInFromConnectionOfEl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val conn = utils.getConnectionFromEl(rctx)
    utils.respondWithTotal(conn.fromRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesInFromConnectionOfTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    val conn = utils.getConnectionFromTl(rctx, tl)
    utils.respondWithTotal(conn.fromRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesInFromConnectionOfSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val socks5 = Application.get().socks5ServerHolder[rctx.param("socks5")]
    val conn = utils.getConnectionFromTl(rctx, socks5)
    utils.respondWithTotal(conn.fromRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesInFromConnectionOfServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val conn = utils.getConnectionFromServer(rctx)
    utils.respondWithTotal(conn.fromRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesInFromServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val svr = utils.getServer(rctx)
    utils.respondWithTotal(svr.fromRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesOutFromL4AddrTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    utils.respondBytesOutFromL4AddrTl(rctx.param("l4addr"), tl, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesOutFromL4AddrSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val socks5 = Application.get().socks5ServerHolder[rctx.param("socks5")]
    utils.respondBytesOutFromL4AddrTl(rctx.param("l4addr"), socks5, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesOutFromConnectionOfEl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val conn = utils.getConnectionFromEl(rctx)
    utils.respondWithTotal(conn.toRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesOutFromConnectionOfTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tl = Application.get().tcpLBHolder[rctx.param("tl")]
    val conn = utils.getConnectionFromTl(rctx, tl)
    utils.respondWithTotal(conn.toRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesOutFromConnectionOfSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val socks5 = Application.get().socks5ServerHolder[rctx.param("socks5")]
    val conn = utils.getConnectionFromTl(rctx, socks5)
    utils.respondWithTotal(conn.toRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesOutFromConnectionOfServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val conn = utils.getConnectionFromServer(rctx)
    utils.respondWithTotal(conn.toRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getBytesOutFromServer(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val svr = utils.getServer(rctx)
    utils.respondWithTotal(svr.toRemoteBytes, cb)
  }

  @Throws(NotFoundException::class)
  private fun getAcceptedConnFromL4AddrTl(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val tlStr: String = rctx.param("tl")
    val l4addrStr: String = rctx.param("l4addr")
    val tl = Application.get().tcpLBHolder[tlStr]
    utils.respondAcceptedConnFromL4AddrTl(l4addrStr, tl, cb)
  }

  @Throws(NotFoundException::class)
  private fun getAcceptedConnFromL4AddrSocks5(rctx: RoutingContext, cb: Callback<JSON.Instance<*>, Throwable>) {
    val socks5Str: String = rctx.param("socks5")
    val l4addrStr: String = rctx.param("l4addr")
    val socks5 = Application.get().socks5ServerHolder[socks5Str]
    utils.respondAcceptedConnFromL4AddrTl(l4addrStr, socks5, cb)
  }

  private suspend fun watchHealthCheck(rctx: RoutingContext) {
    val resp = rctx.conn.response(200)
    resp.sendHeadersBeforeChunks()
    val loop = SelectorEventLoop.current()
    val handler = arrayOfNulls<Consumer<GlobalEvents.Messages.HealthCheck>>(1)
    val channel = Channel<GlobalEvents.Messages.HealthCheck>()
    handler[0] = object : Consumer<GlobalEvents.Messages.HealthCheck> {
      override fun accept(msg: GlobalEvents.Messages.HealthCheck) {
        if (rctx.conn.base().isClosed) {
          assert(Logger.lowLevelDebug("connection closed while sending data, should deregister the handler"))
          GlobalEvents.getInstance().deregister(GlobalEvents.HEALTH_CHECK, handler[0])
          channel.close()
          return
        }
        loop.launch {
          channel.send(msg)
        }
      }
    }
    GlobalEvents.getInstance().register(GlobalEvents.HEALTH_CHECK, handler[0])

    for (msg in channel) {
      val svr: ServerGroup.ServerHandle = msg.server
      val sg: ServerGroup = msg.serverGroup
      val builder: ObjectBuilder = ObjectBuilder()
        .putInst("server", utils.formatServer(svr))
        .putInst("serverGroup", utils.formatServerGroup(sg))
      resp.sendChunk(builder.build())
    }
  }

  internal class Err(val code: Int, override val message: String?) : RuntimeException()

  private class WrappedRoutingHandler(
    val executor: Executor,
    val bodyTemplate: JSON.Object?,
    requiredKeys: Array<String>,
  ) : RoutingHandler {
    private val requiredKeys = listOf(*requiredKeys)

    override suspend fun handle(rctx: RoutingContext) {
      val body = rctx.get(Tool.bodyJson)
      if (bodyTemplate != null) {
        if (body == null) {
          rctx.conn.response(400).send(
            ObjectBuilder()
              .put("code", 400)
              .put("message", "this api must be called with http body: " + bodyTemplate.stringify())
              .build()
          )
          return
        }
        if (body !is JSON.Object) {
          rctx.conn.response(400).send(
            ObjectBuilder()
              .put("code", 400)
              .put("message", "this api only accepts json object from http body: " + bodyTemplate.stringify())
              .build()
          )
          return
        }
        val err = utils.validateBody(bodyTemplate, requiredKeys, body)
        if (err != null) {
          rctx.conn.response(400).send(
            ObjectBuilder()
              .put("code", 400)
              .put("message", err)
              .build()
          )
          return
        }
      } else {
        if (body != null) {
          rctx.conn.response(400).send(
            ObjectBuilder()
              .put("code", 400)
              .put("message", "this api should NOT be called with http body")
              .build()
          )
          return
        }
      }
      try {
        val json = suspendCancellableCoroutine<JSON.Instance<*>?> { cont ->
          executor(rctx, object : Callback<JSON.Instance<*>, Throwable>() {
            override fun onSucceeded(value: JSON.Instance<*>?) {
              cont.resume(value)
            }

            override fun onFailed(err: Throwable) {
              cont.resumeWithException(err)
            }
          })
        }
        handleResult(rctx, null, json)
      } catch (t: Throwable) {
        handleResult(rctx, t, null)
        return
      }
    }
  }

  companion object {
    private const val htmlBase = "/html"
    private const val apiBase = "/api"
    private const val apiV1Base = "$apiBase/v1"
    private const val moduleBase = "$apiV1Base/module"
    private const val channelBase = "$apiV1Base/channel"
    private const val stateBase = "$apiV1Base/state"
    private const val statistics = "$apiV1Base/statistics"
    private const val watch = "$apiV1Base/watch"
    private suspend fun handleResult(rctx: RoutingContext, err: Throwable?, ret: JSON.Instance<*>?) {
      if (err != null) {
        when (err) {
          is Err -> {
            rctx.conn.response(err.code).send(
              ObjectBuilder()
                .put("code", err.code)
                .put("message", err.message)
                .build()
            )
          }
          is NotFoundException -> {
            rctx.conn.response(404).send(
              ObjectBuilder()
                .put("code", 404)
                .put("message", err.message)
                .build()
            )
          }
          is AlreadyExistException -> {
            rctx.conn.response(409).send(
              ObjectBuilder()
                .put("code", 409)
                .put("message", err.message)
                .build()
            )
          }
          is XException -> {
            rctx.conn.response(400).send(
              ObjectBuilder()
                .put("code", 400)
                .put("message", err.message)
                .build()
            )
          }
          else -> {
            val errId: String = UUID.randomUUID().toString()
            Logger.error(LogType.IMPROPER_USE, "http request got error when handling in HttpController. errId=$errId", err)
            rctx.conn.response(500).send(
              ObjectBuilder()
                .put("code", 500)
                .put("errId", errId)
                .put(
                  "message", "something went wrong. " +
                    "please report this to the maintainer with errId=" + errId + ", details will be in the logs. " +
                    "if it's the vproxy community version, you may create an issue on https://github.com/wkgcass/vproxy/issues " +
                    "with related logs"
                )
                .build()
            )
          }
        }
        return
      }
      if (ret == null) {
        rctx.conn.response(204).send()
      } else {
        rctx.conn.response(200).send(ret)
      }
    }

    private fun wrapAsync(func: Executor, bodyTemplate: JSON.Object? = null): WrappedRoutingHandler {
      return wrapAsync(func, bodyTemplate, *arrayOf())
    }

    @Suppress("unchecked_cast")
    private fun wrapAsync(func: Executor, bodyTemplate: JSON.Object?, vararg _requiredKeys: String): WrappedRoutingHandler {
      return WrappedRoutingHandler(func, bodyTemplate, _requiredKeys as Array<String>)
    }
  }
}

internal typealias Executor = (RoutingContext, Callback<JSON.Instance<*>, Throwable>) -> Unit
