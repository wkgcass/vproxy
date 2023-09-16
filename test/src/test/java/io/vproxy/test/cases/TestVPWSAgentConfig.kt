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
        "  }\n" +
        "  proxy {\n" +
        "    auth = alice:pasSw0rD\n" +
        "    hc = true\n" +
        "    groups = [\n" +
        "      {\n" +
        "        servers = [\n" +
        "          'websockss://127.0.0.1:18686'\n" +
        "          'websockss:kcp://example.com:443'\n" +
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
        "                    \"ip\": \"127.0.0.1\",\n" +
        "                    \"port\": 18686\n" +
        "                },\n" +
        "                {\n" +
        "                    \"protocol\": \"websockss\",\n" +
        "                    \"kcp\": {\n" +
        "                        \"enabled\": true,\n" +
        "                        \"uot\": { \"enabled\": false }\n" +
        "                    },\n" +
        "                    \"ip\": \"example.com\",\n" +
        "                    \"port\": 443\n" +
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
      strToCheck = strToCheck.replace(replace, tmp.absolutePath)
      replaceMap[replace] = tmp.absolutePath
    }
    for (replace in listOf("{{ca.key.pem}}", "{{pixiv.key.pem}}", "{{google.key.pem}}")) {
      val x = replace.substring(2, replace.length - 2)
      val tmp = File.createTempFile(x, "")
      tmp.deleteOnExit()
      Files.writeString(tmp.toPath(), TestSSL.TEST_KEY)
      strToParse = strToParse.replace(replace, tmp.absolutePath)
      strToCheck = strToCheck.replace(replace, tmp.absolutePath)
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

    assertEquals(0, loader.validate().size)
  }
}
