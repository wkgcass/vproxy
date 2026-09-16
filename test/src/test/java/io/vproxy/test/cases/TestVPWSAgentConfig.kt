package io.vproxy.test.cases

import vjson.CharStream.Companion.from
import vjson.deserializer.DeserializeParserListener
import vjson.parser.ParserMode
import vjson.parser.ParserOptions.Companion.allFeatures
import vjson.parser.ParserUtils.buildFrom
import io.vproxy.vproxyx.websocks.*
import io.vproxy.vproxyx.websocks.VPWSAgentConfig.Companion.rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TestVPWSAgentConfig {
  companion object {
    private const val SAMPLE_CONFIG = "{\n" +
        "  agent {\n" +
        "    socks5.listen = 1080\n" +
        "    httpconnect.listen = 18080\n" +
        "    ss.listen = 8388\n" +
        "    ss.password = '123456'\n" +
        "    dns.listen = 53\n" +
        "    tls-sni-erasure {\n" +
        "      cert-key.auto-sign = [ {{ca.cert.pem}}, {{ca.key.pem}} ]\n" +
        "      cert-key.list = [\n" +
        "        [{{pixiv.cert.pem}}, {{pixiv.key.pem}}]\n" +
        "        [{{google.cert.pem}}, {{google.key.pem}}]\n" +
        "      ]\n" +
        "      domains = [\n" +
        "        /.*pixiv.*/\n" +
        "      ]\n" +
        "    }\n" +
        "    direct-relay {\n" +
        "      enabled = true\n" +
        "      ip-range = 100.64.0.0/10\n" +
        "      listen = 127.0.0.1:8888\n" +
        "      ip-bond-timeout = 100\n" +
        "    }\n" +
        "    cacerts.path = ./dep/cacerts\n" +
        "    cacerts.pswd = changeit\n" +
        "    cert.verify = true\n" +
        "    gateway = true\n" +
        "    gateway.pac.listen = 20080\n" +
        "    strict = true\n" +
        "    pool = 4\n" +
        "    uot {\n" +
        "      enabled = true\n" +
        "      nic = enp5s0\n" +
        "    }\n" +
        "    quic {\n" +
        "      enabled = true\n" +
        "      cacerts = ./quic.ca.pem\n" +
        "    }\n" +
        "  }\n" +
        "  proxy {\n" +
        "    auth = alice:pasSw0rD\n" +
        "    hc = true\n" +
        "    groups = [\n" +
        "      {\n" +
        "        servers = [\n" +
        "          'websockss://127.0.0.1:18686'\n" +
        "          'websockss:kcp://example.com:443'\n" +
        "          'websocks:quic://my.quic.com:4443'\n" +
        "        ]\n" +
        "        domains = [\n" +
        "          /.*google\\.com.*/\n" +
        "          216.58.200.46\n" +
        "          youtube.com\n" +
        "          zh.wikipedia.org\n" +
        "          id.heroku.com\n" +
        "          baidu.com\n" +
        "          /.*bilibili\\.com$/\n" +
        "        ]\n" +
        "        resolve = [\n" +
        "          pixiv.net\n" +
        "        ]\n" +
        "        no-proxy = [\n" +
        "          /.*pixiv.*/\n" +
        "        ]\n" +
        "      }\n" +
        "      {\n" +
        "        name = TEST\n" +
        "        servers = [\n" +
        "          'websocks://127.0.0.1:18687'\n" +
        "        ]\n" +
        "        domains = [\n" +
        "          :14000\n" +
        "          163.com\n" +
        "        ]\n" +
        "      }\n" +
        "    ]\n" +
        "  }\n" +
        "}\n"

    private const val SAMPLE_RESULT = "{\n" +
        "    \"socks5\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"listen\": 1080\n" +
        "    },\n" +
        "    \"httpconnect\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"listen\": 18080\n" +
        "    },\n" +
        "    \"ss\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"listen\": 8388,\n" +
        "        \"password\": \"123456\"\n" +
        "    },\n" +
        "    \"dns\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"listen\": 53\n" +
        "    },\n" +
        "    \"pac\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"listen\": 20080\n" +
        "    },\n" +
        "    \"gateway\": { \"enabled\": true },\n" +
        "    \"autosign\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"cacert\": \"{{ca.cert.pem}}\",\n" +
        "        \"cakey\": \"{{ca.key.pem}}\"\n" +
        "    },\n" +
        "    \"directrelay\": { \"enabled\": true },\n" +
        "    \"directrelayadvanced\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"network\": \"100.64.0.0/10\",\n" +
        "        \"listen\": \"127.0.0.1:8888\",\n" +
        "        \"timeout\": 6000000\n" +
        "    },\n" +
        "    \"uot\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"nic\": \"enp5s0\"\n" +
        "    },\n" +
        "    \"quic\": {\n" +
        "        \"enabled\": true,\n" +
        "        \"cacerts\": \"./quic.ca.pem\"\n" +
        "    },\n" +
        "    \"serverUser\": \"alice\",\n" +
        "    \"serverPass\": \"pasSw0rD\",\n" +
        "    \"hc\": { \"enabled\": true },\n" +
        "    \"certauth\": { \"enabled\": true },\n" +
        "    \"serverGroupList\": [\n" +
        "        {\n" +
        "            \"name\": \"TEST\",\n" +
        "            \"serverList\": [ {\n" +
        "                \"protocol\": \"websocks\",\n" +
        "                \"kcp\": {\n" +
        "                    \"enabled\": false,\n" +
        "                    \"uot\": { \"enabled\": false }\n" +
        "                },\n" +
        "                \"quic\": { \"enabled\": false },\n" +
        "                \"unet\": { \"enabled\": false },\n" +
        "                \"ip\": \"127.0.0.1\",\n" +
        "                \"port\": 18687\n" +
        "            } ],\n" +
        "            \"proxyRuleList\": [\n" +
        "                {\n" +
        "                    \"rule\": \":14000\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"rule\": \"163.com\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                }\n" +
        "            ],\n" +
        "            \"dnsRuleList\": []\n" +
        "        },\n" +
        "        {\n" +
        "            \"name\": \"DEFAULT\",\n" +
        "            \"serverList\": [\n" +
        "                {\n" +
        "                    \"protocol\": \"websockss\",\n" +
        "                    \"kcp\": {\n" +
        "                        \"enabled\": false,\n" +
        "                        \"uot\": { \"enabled\": false }\n" +
        "                    },\n" +
        "                    \"quic\": { \"enabled\": false },\n" +
        "                    \"unet\": { \"enabled\": false },\n" +
        "                    \"ip\": \"127.0.0.1\",\n" +
        "                    \"port\": 18686\n" +
        "                },\n" +
        "                {\n" +
        "                    \"protocol\": \"websockss\",\n" +
        "                    \"kcp\": {\n" +
        "                        \"enabled\": true,\n" +
        "                        \"uot\": { \"enabled\": false }\n" +
        "                    },\n" +
        "                    \"quic\": { \"enabled\": false },\n" +
        "                    \"unet\": { \"enabled\": false },\n" +
        "                    \"ip\": \"example.com\",\n" +
        "                    \"port\": 443\n" +
        "                },\n" +
        "                {\n" +
        "                    \"protocol\": \"websocks\",\n" +
        "                    \"kcp\": {\n" +
        "                        \"enabled\": false,\n" +
        "                        \"uot\": { \"enabled\": false }\n" +
        "                    },\n" +
        "                    \"quic\": { \"enabled\": true },\n" +
        "                    \"unet\": { \"enabled\": false },\n" +
        "                    \"ip\": \"my.quic.com\",\n" +
        "                    \"port\": 4443\n" +
        "                }\n" +
        "            ],\n" +
        "            \"proxyRuleList\": [\n" +
        "                {\n" +
        "                    \"rule\": \"/.*google\\\\.com.*/\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"rule\": \"216.58.200.46\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"rule\": \"youtube.com\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"rule\": \"zh.wikipedia.org\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"rule\": \"id.heroku.com\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"rule\": \"baidu.com\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"rule\": \"/.*bilibili\\\\.com\$/\",\n" +
        "                    \"white\": { \"enabled\": false }\n" +
        "                },\n" +
        "                {\n" +
        "                    \"rule\": \"/.*pixiv.*/\",\n" +
        "                    \"white\": { \"enabled\": true }\n" +
        "                }\n" +
        "            ],\n" +
        "            \"dnsRuleList\": [ { \"rule\": \"pixiv.net\" } ]\n" +
        "        }\n" +
        "    ],\n" +
        "    \"httpsSniErasureRuleList\": [ { \"rule\": \"/.*pixiv.*/\" } ]\n" +
        "}"
  }

  @Test
  fun allInOne() {
    val listener = DeserializeParserListener(rule)
    buildFrom(
      from(
        SAMPLE_CONFIG
          .replace("{{ca.cert.pem}}", "~/ca.cert.pem")
          .replace("{{ca.key.pem}}", "~/ca.key.pem")
          .replace("{{pixiv.cert.pem}}", "~/pixiv.cert.pem")
          .replace("{{pixiv.key.pem}}", "~/pixiv.key.pem")
          .replace("{{google.cert.pem}}", "~/google.cert.pem")
          .replace("{{google.key.pem}}", "~/google.key.pem")
      ), allFeatures().setListener(listener)
        .setMode(ParserMode.JAVA_OBJECT)
        .setNullArraysAndObjects(true)
    )
    val config = listener.get()
    val expected = VPWSAgentConfig(
      agent = AgentConfig(
        socks5Listen = 1080,
        httpConnectListen = 18080,
        ssListen = 8388,
        ssPassword = "123456",
        dnsListen = 53,
        tlsSniErasure = TlsSniErasureConfig(
          certKeyAutoSign = listOf("~/ca.cert.pem", "~/ca.key.pem"),
          certKeyList = listOf(
            listOf("~/pixiv.cert.pem", "~/pixiv.key.pem"),
            listOf("~/google.cert.pem", "~/google.key.pem"),
          ),
          domains = listOf("/.*pixiv.*/")
        ),
        directRelay = DirectRelayConfig(
          enabled = true,
          ipRange = "100.64.0.0/10",
          listen = "127.0.0.1:8888",
          ipBondTimeout = 100,
        ),
        cacertsPath = "./dep/cacerts",
        cacertsPswd = "changeit",
        certVerify = true,
        gateway = true,
        gatewayPacListen = 20080,
        strict = true,
        pool = 4,
        uot = UOTConfig(
          enabled = true,
          nic = "enp5s0"
        ),
        quic = QuicConfig(
          enabled = true,
          cacerts = "./quic.ca.pem",
        ),
      ),
      proxy = ProxyConfig(
        auth = "alice:pasSw0rD",
        hc = true,
        groups = listOf(
          ProxyServerGroupConfig(
            name = "DEFAULT",
            servers = listOf(
              "websockss://127.0.0.1:18686",
              "websockss:kcp://example.com:443",
              "websocks:quic://my.quic.com:4443",
            ),
            domains = listOf(
              "/.*google\\.com.*/",
              "216.58.200.46",
              "youtube.com",
              "zh.wikipedia.org",
              "id.heroku.com",
              "baidu.com",
              "/.*bilibili\\.com\$/",
            ),
            resolve = listOf(
              "pixiv.net",
            ),
            noProxy = listOf(
              "/.*pixiv.*/"
            ),
          ),
          ProxyServerGroupConfig(
            name = "TEST",
            servers = listOf(
              "websocks://127.0.0.1:18687",
            ),
            domains = listOf(
              ":14000",
              "163.com",
            ),
          ),
        ),
      )
    )
    assertEquals(expected, config)
  }

  @Test
  fun configLoader() {
    var strToParse = SAMPLE_CONFIG
    var strToCheck = SAMPLE_RESULT
    val replaceMap = HashMap<String, String>()
    for (replace in listOf("{{ca.cert.pem}}", "{{pixiv.cert.pem}}", "{{google.cert.pem}}")) {
      val x = replace.substring(2, replace.length - 2)
      val tmp = File.createTempFile(x, "")
      tmp.deleteOnExit()
      Files.writeString(tmp.toPath(), TestSSL.TEST_CERT)
      strToParse = strToParse.replace(replace, tmp.absolutePath)
      // the path is embedded into an expected json string, so backslashes (windows) must be escaped
      strToCheck = strToCheck.replace(replace, tmp.absolutePath.replace("\\", "\\\\"))
      replaceMap[replace] = tmp.absolutePath
    }
    for (replace in listOf("{{ca.key.pem}}", "{{pixiv.key.pem}}", "{{google.key.pem}}")) {
      val x = replace.substring(2, replace.length - 2)
      val tmp = File.createTempFile(x, "")
      tmp.deleteOnExit()
      Files.writeString(tmp.toPath(), TestSSL.TEST_KEY)
      strToParse = strToParse.replace(replace, tmp.absolutePath)
      // the path is embedded into an expected json string, so backslashes (windows) must be escaped
      strToCheck = strToCheck.replace(replace, tmp.absolutePath.replace("\\", "\\\\"))
      replaceMap[replace] = tmp.absolutePath
    }

    val tmpFile = File.createTempFile("vpws-agent", ".conf")
    tmpFile.deleteOnExit()
    Files.writeString(tmpFile.toPath(), strToParse)
    val loader = ConfigLoader()
    loader.load(tmpFile.absolutePath)
    assertEquals(strToCheck, loader.toJson().pretty())

    // check extra fields not present in json
    // tls-sni-erasure { cert-key.list: [] }
    assertEquals(
      listOf(
        listOf(replaceMap["{{pixiv.cert.pem}}"], replaceMap["{{pixiv.key.pem}}"]),
        listOf(replaceMap["{{google.cert.pem}}"], replaceMap["{{google.key.pem}}"]),
      ), loader.httpsSniErasureCertKeyFiles
    )
    // cacerts.path,pswd
    assertEquals("./dep/cacerts", loader.cacertsPath)
    assertEquals("changeit", loader.cacertsPswd)
    // strict
    assertTrue(loader.isStrictMode)
    // pool
    assertEquals(4, loader.poolSize)

    val validationResult = loader.validate()
    assertEquals(1, validationResult.size)
    assertEquals(
      listOf("agent.uot and agent.quic cannot be enabled at the same time"),
      validationResult
    )
  }

  private fun loadConfig(content: String): ConfigLoader {
    val tmpFile = File.createTempFile("vpws-agent", ".conf")
    tmpFile.deleteOnExit()
    Files.writeString(tmpFile.toPath(), content)
    val loader = ConfigLoader()
    loader.load(tmpFile.absolutePath)
    return loader
  }

  private fun tunConfig(directRelayEnabled: String, tunBlock: String): String {
    return "{\n" +
      "  agent {\n" +
      "    direct-relay {\n" +
      "      enabled = " + directRelayEnabled + "\n" +
      "      ip-range = 100.64.0.0/10\n" +
      "      ip6-range = fd00::/96\n" +
      tunBlock +
      "    }\n" +
      "  }\n" +
      "  proxy {\n" +
      "    auth = user:pass\n" +
      "    groups = [ { servers = ['websocks://127.0.0.1:19999'] } ]\n" +
      "  }\n" +
      "}\n"
  }

  @Test
  fun directRelayTun() {
    // valid config
    val loader = loadConfig(tunConfig("true", "" +
      "      tun {\n" +
      "        enabled = true\n" +
      "        dev = tun100\n" +
      "        dns-ip = 100.64.0.53\n" +
      "        dns-ip6 = fd00::53\n" +
      "      }\n"))
    assertTrue(loader.isDirectRelayTunEnabled)
    assertEquals("tun100", loader.directRelayTunDev)
    assertEquals(io.vproxy.vfd.IP.from("100.64.0.53"), loader.directRelayTunDnsIP)
    assertEquals(io.vproxy.vfd.IP.from("fd00::53"), loader.directRelayTunDnsIP6)
    assertEquals(emptyList<String>(), loader.validate())

    // tun enabled but direct-relay disabled
    var l = loadConfig(tunConfig("false", "" +
      "      tun {\n" +
      "        enabled = true\n" +
      "        dns-ip = 100.64.0.53\n" +
      "        dns-ip6 = fd00::53\n" +
      "      }\n"))
    assertTrue(
      l.validate().contains("agent.direct-relay.tun is enabled, but agent.direct-relay is not enabled")
    )

    // tun enabled together with tproxy listen
    l = loadConfig(tunConfig("true", "" +
      "      listen = 127.0.0.1:8888\n" +
      "      tun {\n" +
      "        enabled = true\n" +
      "        dns-ip = 100.64.0.53\n" +
      "        dns-ip6 = fd00::53\n" +
      "      }\n"))
    assertEquals(
      listOf("agent.direct-relay.tun is enabled, but agent.direct-relay.listen is set, which is only used for tproxy mode"),
      l.validate()
    )

    // dns-ip out of the ip-range
    l = loadConfig(tunConfig("true", "" +
      "      tun {\n" +
      "        enabled = true\n" +
      "        dns-ip = 192.168.1.53\n" +
      "        dns-ip6 = fd00::53\n" +
      "      }\n"))
    assertEquals(
      listOf("agent.direct-relay.tun.dns-ip 192.168.1.53 is not inside agent.direct-relay.ip-range 100.64.0.0/10"),
      l.validate()
    )

    // ip6-range is set but dns-ip6 is missing
    l = loadConfig(tunConfig("true", "" +
      "      tun {\n" +
      "        enabled = true\n" +
      "        dns-ip = 100.64.0.53\n" +
      "      }\n"))
    assertEquals(
      listOf("agent.direct-relay.tun is enabled with ip6-range, but agent.direct-relay.tun.dns-ip6 is not set"),
      l.validate()
    )

    // tun cannot be enabled with unet
    l = loadConfig("{\n" +
      "  agent {\n" +
      "    direct-relay {\n" +
      "      enabled = true\n" +
      "      ip-range = 100.64.0.0/10\n" +
      "      tun {\n" +
      "        enabled = true\n" +
      "        dns-ip = 100.64.0.53\n" +
      "      }\n" +
      "    }\n" +
      "    unet {\n" +
      "      enabled = true\n" +
      "    }\n" +
      "  }\n" +
      "  proxy {\n" +
      "    auth = user:pass\n" +
      "    groups = [ { servers = ['websocks://127.0.0.1:19999'] } ]\n" +
      "  }\n" +
      "}\n")
    assertEquals(
      listOf("agent.direct-relay.tun and agent.unet cannot be enabled at the same time"),
      l.validate()
    )

    // host-ip/host-ip6 are configurable, default derived from the range
    l = loadConfig(tunConfig("true", """
      tun {
        enabled = true
        dns-ip = 100.64.0.53
        dns-ip6 = fd00::53
        host-ip = 100.64.0.9
        host-ip6 = fd00::9
      }
    """))
    assertEquals(io.vproxy.vfd.IP.from("100.64.0.9"), l.directRelayTunHostIP)
    assertEquals(io.vproxy.vfd.IP.from("fd00::9"), l.directRelayTunHostIP6)
    assertEquals(emptyList<String>(), l.validate())

    // dns-ip conflicts with the configured host-ip
    l = loadConfig(tunConfig("true", """
      tun {
        enabled = true
        dns-ip = 100.64.0.53
        dns-ip6 = fd00::53
        host-ip = 100.64.0.53
      }
    """))
    assertEquals(
      listOf("agent.direct-relay.tun.dns-ip conflicts with the reserved tun host address"),
      l.validate()
    )

    // host-ip out of the ip-range
    l = loadConfig(tunConfig("true", """
      tun {
        enabled = true
        dns-ip = 100.64.0.53
        dns-ip6 = fd00::53
        host-ip = 192.168.1.1
      }
    """))
    assertEquals(
      listOf("agent.direct-relay.tun.host-ip 192.168.1.1 is not inside agent.direct-relay.ip-range 100.64.0.0/10"),
      l.validate()
    )

    // host-ip6 set without ip6-range
    l = loadConfig("{\n" +
      "  agent {\n" +
      "    direct-relay {\n" +
      "      enabled = true\n" +
      "      ip-range = 100.64.0.0/10\n" +
      "      tun {\n" +
      "        enabled = true\n" +
      "        dns-ip = 100.64.0.53\n" +
      "        host-ip6 = fd00::1\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "  proxy {\n" +
      "    auth = user:pass\n" +
      "    groups = [ { servers = ['websocks://127.0.0.1:19999'] } ]\n" +
      "  }\n" +
      "}\n")
    assertEquals(
      listOf("agent.direct-relay.tun.host-ip6 is set, but agent.direct-relay.ip6-range is not set"),
      l.validate()
    )
  }

  @Test
  fun directRelayTunReservedHost() {
    val loader = loadConfig(tunConfig("true", """
      tun {
        enabled = true
        dns-ip = 100.64.0.1
        dns-ip6 = fd00::1
      }
    """))
    assertEquals(listOf(
      "agent.direct-relay.tun.dns-ip conflicts with the reserved tun host address",
      "agent.direct-relay.tun.dns-ip6 conflicts with the reserved tun host address",
    ), loader.validate())
  }

  @Test
  fun directRelayTunInvalidIp() {
    try {
      loadConfig(tunConfig("true", """
        tun { enabled = true, dns-ip = invalid, dns-ip6 = fd00::53 }
      """))
      throw AssertionError("invalid IP was accepted")
    } catch (e: Exception) {
      assertTrue(e.message.orEmpty().contains("agent.direct-relay.tun.dns-ip"))
    }
  }
}
