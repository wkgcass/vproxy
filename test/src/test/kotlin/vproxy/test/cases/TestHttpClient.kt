package vproxy.test.cases

import org.junit.Test
import vproxy.lib.http1.CoroutineHttp1ClientConnection

class TestHttpClient {
  @Test
  fun simpleClient() {
    println(CoroutineHttp1ClientConnection.simpleGet("https://gitee.com/wkgcass/gfwlist/raw/master/gfwlist.txt").block())
  }
}
