package io.vproxy.test.cases

import io.vproxy.lib.http1.CoroutineHttp1ClientConnection
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

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
