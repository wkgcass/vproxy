package io.vproxy.vproxyx.websocks

import vjson.deserializer.rule.*

data class VPWSAgentConfig(
  // agent
  var agent: AgentConfig = AgentConfig(),
  // proxy
  var proxy: ProxyConfig = ProxyConfig(),
) {
  companion object {
    val rule: Rule<VPWSAgentConfig> = ObjectRule { VPWSAgentConfig() }
      .put("agent", AgentConfig.rule) { agent = it }
      .put("proxy", ProxyConfig.rule) { proxy = it }
  }
}

data class AgentConfig(
  // socks5.listen
  var socks5Listen: Int = 0,
  // httpconnect.listen
  var httpConnectListen: Int = 0,
  // ss.listen
  var ssListen: Int = 0,
  // ss.password
  var ssPassword: String = "",
  // dns.listen
  var dnsListen: Int = 0,
  // tls-sni-erasure
  var tlsSniErasure: TlsSniErasureConfig? = null,
  // direct-relay
  var directRelay: DirectRelayConfig? = null,
  // cacerts.path
  var cacertsPath: String = "",
  // cacerts.pswd
  var cacertsPswd: String = "",
  // cert.verify
  var certVerify: Boolean = true,
  // gateway
  var gateway: Boolean = false,
  // gateway.pac.listen
  var gatewayPacListen: Int = 0,
  // strict
  var strict: Boolean = false,
  // pool
  var pool: Int = 10,
  // uot
  var uot: UOTConfig = UOTConfig(),
  // quic
  var quic: QuicConfig = QuicConfig(),
  // unet
  var unet: UNetConfig = UNetConfig(),
  // timeout
  var timeout: Int = 60 * 1000,
) {
  companion object {
    val rule: Rule<AgentConfig> = ObjectRule { AgentConfig() }
      .put("socks5.listen", IntRule) { socks5Listen = it }
      .put("httpconnect.listen", IntRule) { httpConnectListen = it }
      .put("ss.listen", IntRule) { ssListen = it }
      .put("ss.password", StringRule) { ssPassword = it }
      .put("dns.listen", IntRule) { dnsListen = it }
      .put("tls-sni-erasure", TlsSniErasureConfig.rule) { tlsSniErasure = it }
      .put("direct-relay", DirectRelayConfig.rule) { directRelay = it }
      .put("cacerts.path", StringRule) { cacertsPath = it }
      .put("cacerts.pswd", StringRule) { cacertsPswd = it }
      .put("cert.verify", BoolRule) { certVerify = it }
      .put("gateway", BoolRule) { gateway = it }
      .put("gateway.pac.listen", IntRule) { gatewayPacListen = it }
      .put("strict", BoolRule) { strict = it }
      .put("pool", IntRule) { pool = it }
      .put("uot", UOTConfig.rule) { uot = it }
      .put("quic", QuicConfig.rule) { quic = it }
      .put("unet", UNetConfig.rule) { unet = it }
      .put("timeout", IntRule) { timeout = it }
  }
}

data class TlsSniErasureConfig(
  // cert-key.auto-sign
  var certKeyAutoSign: List<String> = listOf(),
  // cert-key.list
  var certKeyList: List<List<String>> = listOf(),
  // domains
  var domains: List<String> = listOf(),
) {
  companion object {
    val rule: Rule<TlsSniErasureConfig> = ObjectRule { TlsSniErasureConfig() }
      .put("cert-key.auto-sign", ArrayRule<ArrayList<String>, String>({ ArrayList() }, { add(it) }, StringRule)) { certKeyAutoSign = it }
      .put("cert-key.list", ArrayRule<ArrayList<ArrayList<String>>, ArrayList<String>>({ ArrayList() }, { add(it) },
        ArrayRule({ ArrayList() }, { add(it) }, StringRule)
      )
      ) { certKeyList = it }
      .put("domains", ArrayRule<ArrayList<String>, String>({ ArrayList() }, { add(it) }, StringRule)) { domains = it }
  }
}

data class DirectRelayConfig(
  // enabled
  var enabled: Boolean = false,
  // ip-range
  var ipRange: String = "",
  // ip6-range
  var ip6Range: String = "",
  // listen
  var listen: String = "",
  // listen6
  var listen6: String = "",
  // ip-bond-timeout
  var ipBondTimeout: Int = 10,
  // tun
  var tun: DirectRelayTunConfig? = null,
) {
  companion object {
    val rule: Rule<DirectRelayConfig> = ObjectRule { DirectRelayConfig() }
      .put("enabled", BoolRule) { enabled = it }
      .put("ip-range", StringRule) { ipRange = it }
      .put("ip6-range", StringRule) { ip6Range = it }
      .put("listen", StringRule) { listen = it }
      .put("listen6", StringRule) { listen6 = it }
      .put("ip-bond-timeout", IntRule) { ipBondTimeout = it }
      .put("tun", DirectRelayTunConfig.rule) { tun = it }
  }
}

data class DirectRelayTunConfig(
  // enabled
  // run the direct-relay and dns servers on the userspace tcp/ip stack
  // with a tun device created by the agent itself (for now)
  var enabled: Boolean = false,
  // dev
  // the tun device name; Linux accepts a pattern such as "tun%d"
  var dev: String = "tun",
  // mac
  // the tun peer mac; the stack uses a distinct mac derived from this address
  var mac: String = "00:00:00:05:05:05",
  // post-script
  // a script executed after the tun device is created, with env DEV/VRF/SWITCH
  var postScript: String = "",
  // dns-ip
  // the ipv4 address of the dns server inside ip-range, e.g. 100.64.0.53
  var dnsIP: String = "",
  // dns-ip6
  // the ipv6 address of the dns server inside ip6-range, e.g. fd00::53
  var dnsIP6: String = "",
  // host-ip
  // the ipv4 address reserved for the host side of the tun device, inside ip-range;
  // defaults to the first host address of ip-range (network address + 1)
  var hostIP: String = "",
  // host-ip6
  // the ipv6 address reserved for the host side of the tun device, inside ip6-range;
  // defaults to the first host address of ip6-range (network address + 1)
  var hostIP6: String = "",
) {
  companion object {
    val rule: Rule<DirectRelayTunConfig> = ObjectRule { DirectRelayTunConfig() }
      .put("enabled", BoolRule) { enabled = it }
      .put("dev", StringRule) { dev = it }
      .put("mac", StringRule) { mac = it }
      .put("post-script", StringRule) { postScript = it }
      .put("dns-ip", StringRule) { dnsIP = it }
      .put("dns-ip6", StringRule) { dnsIP6 = it }
      .put("host-ip", StringRule) { hostIP = it }
      .put("host-ip6", StringRule) { hostIP6 = it }
  }
}

data class UOTConfig(
  // enabled
  var enabled: Boolean = false,
  // nic
  var nic: String = "eth0",
) {
  companion object {
    val rule: Rule<UOTConfig> = ObjectRule { UOTConfig() }
      .put("enabled", BoolRule) { enabled = it }
      .put("nic", StringRule) { nic = it }
  }
}

data class QuicConfig(
  // enabled
  var enabled: Boolean = false,
  var cacerts: String? = null,
) {
  companion object {
    val rule: Rule<QuicConfig> = ObjectRule { QuicConfig() }
      .put("enabled", BoolRule) { enabled = it }
      .put("cacerts", NullableStringRule) { cacerts = it }
  }
}

data class UNetConfig(
  // enabled
  var enabled: Boolean = false,
  // nic
  var nic: String = "eth0",
  // multiplier.psh
  var pshMultiplier: Int = 1,
  // multiplier.ack
  var ackMultiplier: Int = 1,
) {
  companion object {
    val rule: Rule<UNetConfig> = ObjectRule { UNetConfig() }
      .put("enabled", BoolRule) { enabled = it }
      .put("nic", StringRule) { nic = it }
      .put("multiplier.psh", IntRule) { pshMultiplier = it }
      .put("multiplier.ack", IntRule) { ackMultiplier = it }
  }
}

data class ProxyConfig(
  // auth
  var auth: String = "",
  // hc
  var hc: Boolean = true,
  // groups
  var groups: List<ProxyServerGroupConfig> = listOf(),
) {
  companion object {
    val rule: Rule<ProxyConfig> = ObjectRule { ProxyConfig() }
      .put("auth", StringRule) { auth = it }
      .put("hc", BoolRule) { hc = it }
      .put(
        "groups",
        ArrayRule<ArrayList<ProxyServerGroupConfig>, ProxyServerGroupConfig>({ ArrayList() }, { add(it) }, ProxyServerGroupConfig.rule)
      ) { groups = it }

  }
}

data class ProxyServerGroupConfig(
  // name
  var name: String = "DEFAULT",
  // servers
  var servers: List<String> = listOf(),
  // domains
  var domains: List<String> = listOf(),
  // resolve
  var resolve: List<String> = listOf(),
  // no-proxy
  var noProxy: List<String> = listOf(),
) {
  companion object {
    val rule: Rule<ProxyServerGroupConfig> = ObjectRule { ProxyServerGroupConfig() }
      .put("name", StringRule) { name = it }
      .put("servers", ArrayRule<ArrayList<String>, String>({ ArrayList() }, { add(it) }, StringRule)) { servers = it }
      .put("domains", ArrayRule<ArrayList<String>, String>({ ArrayList() }, { add(it) }, StringRule)) { domains = it }
      .put("resolve", ArrayRule<ArrayList<String>, String>({ ArrayList() }, { add(it) }, StringRule)) { resolve = it }
      .put("no-proxy", ArrayRule<ArrayList<String>, String>({ ArrayList() }, { add(it) }, StringRule)) { noProxy = it }
  }
}
