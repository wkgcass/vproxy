package io.vproxy.test.cases

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import vproxy.lib.http1.CoroutineHttp1ClientConnection

class TestHttpClient {
  @Ignore
  @Test
  fun simpleClient() {
    assertEquals(
      191373,
      CoroutineHttp1ClientConnection.simpleGet("https://gitee.com/wkgcass/gfwlist/raw/master/gfwlist.txt").block().length()
    )
  }
}
