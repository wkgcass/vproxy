package io.vproxy.vproxyx

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
    if (!io.vproxy.base.Config.dhcpGetDnsListEnabled) {
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
        .get("/") {
          it.conn.response(200)
            .header("content-type", "text/html; charset=utf-8")
            .send(io.vproxy.base.util.ByteArray.from(indexPage().toByteArray(Charsets.UTF_8)))
        }
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
    if (true) {
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
    if (true) {
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

  // generate the html page served on GET /
  private fun indexPage(): String {
    fun esc(s: String?): String {
      if (s == null) {
        return ""
      }
      val sb = StringBuilder()
      for (c in s) {
        when (c) {
          '&' -> sb.append("&amp;")
          '<' -> sb.append("&lt;")
          '>' -> sb.append("&gt;")
          '"' -> sb.append("&quot;")
          '\'' -> sb.append("&#39;")
          else -> sb.append(c)
        }
      }
      return sb.toString()
    }

    fun badge(name: String, on: Boolean): String {
      return if (on) {
        "<span class=\"badge on\">" + esc(name) + "</span>"
      } else {
        "<span class=\"badge\">" + esc(name) + "</span>"
      }
    }

    fun kvRows(map: Map<String, String>): String {
      val sb = StringBuilder()
      for ((k, v) in map) {
        val vv = if (v.isEmpty()) "<span class=\"empty\">(empty)</span>" else esc(v)
        sb.append("<tr><td class=\"k\">").append(esc(k)).append("</td><td class=\"v\">").append(vv).append("</td></tr>")
      }
      return sb.toString()
    }

    val version = io.vproxy.base.util.Version.VERSION
    val now = System.currentTimeMillis()
    val timeStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
      .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(now))
    val pid = ProcessHandle.current().pid()

    val osName = io.vproxy.base.util.OS.name()
    val osVersion = io.vproxy.base.util.OS.version()
    val osArch = io.vproxy.base.util.OS.arch()
    val osDist = io.vproxy.base.util.OS.dist()
    val isLinux = io.vproxy.base.util.OS.isLinux()
    val osBadges = badge("Windows", io.vproxy.base.util.OS.isWindows()) + " " +
      badge("Linux", isLinux) + " " +
      badge("macOS", io.vproxy.base.util.OS.isMac()) + " " +
      badge("iOS", io.vproxy.base.util.OS.isIOS())
    val osTypeOn = when {
      io.vproxy.base.util.OS.isWindows() -> badge("Windows", true)
      isLinux -> badge("Linux", true)
      io.vproxy.base.util.OS.isMac() -> badge("macOS", true)
      io.vproxy.base.util.OS.isIOS() -> badge("iOS", true)
      else -> ""
    }

    var osRows = ""
    osRows += "<tr><td class=\"k\">os.name</td><td class=\"v\">" + esc(osName) + "</td></tr>"
    osRows += "<tr><td class=\"k\">os.version</td><td class=\"v\">" + esc(osVersion) + "</td></tr>"
    osRows += "<tr><td class=\"k\">os.arch</td><td class=\"v\">" + esc(osArch) + "</td></tr>"
    osRows += "<tr><td class=\"k\">type</td><td class=\"v\">" + osBadges + "</td></tr>"
    if (osDist.isNotEmpty()) {
      osRows += "<tr><td class=\"k\">dist</td><td class=\"v\">" + esc(osDist) + "</td></tr>"
    }
    if (isLinux) {
      var kernel = "" + io.vproxy.base.util.OS.major() + "." + io.vproxy.base.util.OS.minor() + "." + io.vproxy.base.util.OS.patch()
      val suffix = io.vproxy.base.util.OS.osVersionSuffix()
      if (suffix.isNotEmpty()) {
        kernel += " (" + esc(suffix) + ")"
      }
      osRows += "<tr><td class=\"k\">linux kernel</td><td class=\"v\">" + kernel + "</td></tr>"
    }

    val props: Map<String, String> = System.getProperties().entries.associate { e -> e.key.toString() to e.value.toString() }
    val env: Map<String, String> = System.getenv()
    val propRows = kvRows(props.toSortedMap())
    val envRows = kvRows(env.toSortedMap())
    val userName = props["user.name"] ?: "-"

    return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>vproxy ${esc(version)} · hello world</title>
<style>
*{box-sizing:border-box}
body{margin:0;background:#0b1220;color:#e2e8f0;font:15px/1.55 ui-sans-serif,system-ui,-apple-system,"Segoe UI",Roboto,"PingFang SC","Hiragino Sans GB","Microsoft YaHei",Arial,sans-serif}
.wrap{max-width:1060px;margin:0 auto;padding:0 24px}
.hero{padding:52px 0 34px;border-bottom:1px solid #1c2942;background:radial-gradient(900px 340px at 15% -20%,rgba(34,211,238,.14),transparent),radial-gradient(700px 300px at 85% -30%,rgba(129,140,248,.12),transparent)}
.pill{display:inline-block;font-size:12px;letter-spacing:.14em;text-transform:uppercase;color:#67e8f9;border:1px solid rgba(34,211,238,.35);border-radius:999px;padding:3px 12px;margin-bottom:14px}
h1{margin:0;font-size:38px;letter-spacing:-.5px}
h1 .ver{font-size:20px;vertical-align:middle;margin-left:10px;padding:3px 12px;border-radius:8px;color:#0b1220;background:linear-gradient(90deg,#22d3ee,#818cf8);font-weight:700}
.hero .sub{margin:10px 0 0;color:#8fa3bf;font-size:14px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:14px;margin:26px 0 8px}
.card{background:#111a2e;border:1px solid #1e2a41;border-radius:14px;padding:16px 18px;display:flex;flex-direction:column}
.card .label{font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:#8fa3bf}
.card .value{margin-top:7px;font-size:19px;font-weight:600;word-break:break-all}
.card .sub{margin-top:auto;padding-top:8px;font-size:12.5px;color:#8fa3bf;word-break:break-all}
h2{display:flex;align-items:center;gap:10px;font-size:17px;margin:34px 0 12px}
.count{font-size:11.5px;font-weight:500;color:#8fa3bf;background:#0e1626;border:1px solid #1e2a41;border-radius:999px;padding:2px 10px;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
.toolbar{position:sticky;top:0;z-index:9;padding:12px 0;background:linear-gradient(#0b1220 82%,transparent)}
.toolbar input{width:100%;max-width:460px;padding:9px 14px;font-size:14px;color:#e2e8f0;background:#0e1626;border:1px solid #24344f;border-radius:10px;outline:none}
.toolbar input:focus{border-color:#22d3ee;box-shadow:0 0 0 3px rgba(34,211,238,.15)}
.table-card{border:1px solid #1e2a41;border-radius:14px;max-height:520px;overflow-y:auto;overflow-x:auto}
table{width:100%;border-collapse:collapse;font-size:13.5px}
th,td{padding:8px 16px;text-align:left;vertical-align:top;border-bottom:1px solid #182438}
thead th{position:sticky;top:0;z-index:2;background:#111a2e;color:#8fa3bf;font-size:11px;letter-spacing:.12em;text-transform:uppercase}
tbody tr:hover{background:rgba(34,211,238,.06)}
td.k{white-space:nowrap;color:#67e8f9;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
td.v{word-break:break-all;color:#cbd5e1;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
.empty{color:#526a8c;font-style:italic}
.badge{display:inline-block;font-size:11px;padding:1px 9px;border-radius:999px;border:1px solid #24344f;color:#8fa3bf}
.badge.on{color:#042026;background:linear-gradient(90deg,#22d3ee,#38bdf8);border-color:transparent;font-weight:700}
.btn{margin-top:20px;padding:9px 20px;font-size:14px;font-weight:600;border-radius:10px;border:1px solid rgba(34,211,238,.4);background:rgba(34,211,238,.1);color:#67e8f9;cursor:pointer;font-family:inherit}
.btn:hover{background:rgba(34,211,238,.2);border-color:#22d3ee}
.btn:active{transform:translateY(1px)}
.btn:disabled{opacity:.75;cursor:default}
footer{margin:44px 0 30px;text-align:center;color:#526a8c;font-size:12.5px}
@media (max-width:640px){
.wrap{padding:0 14px}
.hero{padding:32px 0 24px}
h1{font-size:27px}
h1 .ver{font-size:14px;margin-left:8px;padding:2px 9px}
.hero .sub{font-size:12.5px}
.cards{grid-template-columns:1fr;gap:10px;margin:20px 0 6px}
.card{padding:13px 15px}
.card .value{font-size:17px}
h2{font-size:15.5px;margin:26px 0 10px}
th,td{padding:6px 12px}
td.k{white-space:normal;word-break:break-all}
.toolbar input{max-width:none}
.btn{width:100%}
.table-card{max-height:420px}
footer{margin:32px 0 22px}
}
</style>
</head>
<body>
<div class="hero">
  <div class="wrap">
    <div class="pill">vproxy hello world</div>
    <h1>vproxy <span class="ver">${esc(version)}</span></h1>
    <p class="sub">service info page · generated at ${esc(timeStr)} (epoch ${now} ms)</p>
    <button id="copy-all" class="btn" type="button">复制全部信息</button>
  </div>
</div>
<div class="wrap">
  <div class="cards">
    <div class="card">
      <div class="label">Version</div>
      <div class="value">${esc(version)}</div>
      <div class="sub">io.vproxy.base.util.Version</div>
    </div>
    <div class="card">
      <div class="label">Server Time</div>
      <div class="value" id="clock">${esc(timeStr)}</div>
      <div class="sub">epoch ms: <span id="epoch">${now}</span></div>
    </div>
    <div class="card">
      <div class="label">Operating System</div>
      <div class="value">${esc(osName)}</div>
      <div class="sub">${esc(osArch)} · $osTypeOn</div>
    </div>
    <div class="card">
      <div class="label">Process</div>
      <div class="value">pid ${pid}</div>
      <div class="sub">user ${esc(userName)}</div>
    </div>
  </div>

  <h2>操作系统信息 <span class="count">io.vproxy.base.util.OS</span></h2>
  <div class="table-card">
    <table id="os-table" data-title="操作系统信息 (io.vproxy.base.util.OS)">
      <thead><tr><th style="width:30%">field</th><th>value</th></tr></thead>
      <tbody>
$osRows
      </tbody>
    </table>
  </div>

  <div class="toolbar">
    <input id="filter-input" type="text" placeholder="筛选系统属性与环境变量 (filter properties &amp; env vars) ..." autocomplete="off" spellcheck="false">
  </div>

  <h2>系统属性 <span class="count" data-count-for="props-table">${props.size} items</span></h2>
  <div class="table-card">
    <table id="props-table" class="kv" data-title="系统属性 (System.getProperties)">
      <thead><tr><th style="width:36%">key</th><th>value</th></tr></thead>
      <tbody>
$propRows
      </tbody>
    </table>
  </div>

  <h2>环境变量 <span class="count" data-count-for="env-table">${env.size} items</span></h2>
  <div class="table-card">
    <table id="env-table" class="kv" data-title="环境变量 (System.getenv)">
      <thead><tr><th style="width:36%">name</th><th>value</th></tr></thead>
      <tbody>
$envRows
      </tbody>
    </table>
  </div>

  <footer>vproxy ${esc(version)} · generated at ${esc(timeStr)} · epoch ${now} ms</footer>
</div>
<script>
(function () {
  var t = ${now};
  var clockEl = document.getElementById('clock');
  var epochEl = document.getElementById('epoch');
  function pad(n, w) { n = String(n); while (n.length < w) n = '0' + n; return n; }
  function fmt(ms) {
    var d = new Date(ms);
    return d.getUTCFullYear() + '-' + pad(d.getUTCMonth() + 1, 2) + '-' + pad(d.getUTCDate(), 2) +
      ' ' + pad(d.getUTCHours(), 2) + ':' + pad(d.getUTCMinutes(), 2) + ':' + pad(d.getUTCSeconds(), 2) + ' UTC';
  }
  function render() {
    if (clockEl) { clockEl.textContent = fmt(t); }
    if (epochEl) { epochEl.textContent = String(t); }
  }
  render();
  setInterval(function () { t += 1000; render(); }, 1000);

  var input = document.getElementById('filter-input');
  input.addEventListener('input', function () {
    var kw = input.value.trim().toLowerCase();
    ['props-table', 'env-table'].forEach(function (id) {
      var table = document.getElementById(id);
      if (!table) { return; }
      var shown = 0;
      var rows = table.querySelectorAll('tbody tr');
      for (var i = 0; i < rows.length; ++i) {
        var tr = rows[i];
        var hit = kw === '' || tr.textContent.toLowerCase().indexOf(kw) !== -1;
        tr.style.display = hit ? '' : 'none';
        if (hit) { ++shown; }
      }
      var b = document.querySelector('[data-count-for="' + id + '"]');
      if (b) { b.textContent = shown + ' items'; }
    });
  });

  var copyBtn = document.getElementById('copy-all');
  function buildInfoText() {
    var out = [];
    out.push(document.title);
    var heroSub = document.querySelector('.hero .sub');
    if (heroSub) { out.push(heroSub.textContent.trim()); }
    out.push('');
    out.push('== 概览 ==');
    var cards = document.querySelectorAll('.card');
    for (var i = 0; i < cards.length; ++i) {
      var c = cards[i];
      var label = c.querySelector('.label');
      var value = c.querySelector('.value');
      var csub = c.querySelector('.sub');
      var line = (label ? label.textContent.trim() : '') + ': ' + (value ? value.textContent.trim() : '');
      if (csub) { line += ' (' + csub.textContent.trim() + ')'; }
      out.push(line);
    }
    var tableIds = ['os-table', 'props-table', 'env-table'];
    for (var j = 0; j < tableIds.length; ++j) {
      var t = document.getElementById(tableIds[j]);
      if (!t) { continue; }
      out.push('');
      out.push('== ' + (t.getAttribute('data-title') || tableIds[j]) + ' ==');
      var rows = t.querySelectorAll('tbody tr');
      for (var k = 0; k < rows.length; ++k) {
        var tds = rows[k].querySelectorAll('td');
        if (tds.length >= 2) {
          out.push(tds[0].textContent.trim() + ' = ' + tds[1].textContent.trim());
        }
      }
    }
    return out.join('\n');
  }
  function copyFeedback(ok) {
    var old = '复制全部信息';
    copyBtn.textContent = ok ? '已复制到剪贴板' : '复制失败，请手动选择';
    copyBtn.disabled = true;
    setTimeout(function () {
      copyBtn.textContent = old;
      copyBtn.disabled = false;
    }, 1600);
  }
  function copyFallback(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    var ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    document.body.removeChild(ta);
    copyFeedback(ok);
  }
  function copyAll() {
    var text = buildInfoText();
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () {
        copyFeedback(true);
      }, function () {
        copyFallback(text);
      });
    } else {
      copyFallback(text);
    }
  }
  if (copyBtn) { copyBtn.addEventListener('click', copyAll); }
})();
</script>
</body>
</html>
""".trimIndent()
  }
}
