package vproxy.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import vproxy.test.cases.*;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    BeforeAll.class,

    TestTcpLB.class,
    TestIpParser.class,
    TestNetMask.class,
    TestTimer.class,
    TestResolver.class,
    TestSocks5.class,
    TestConnectClient.class,
    TestSSL.class,
    TestProtocols.class,
    TestHttp1Parser.class,
    TestHealthCheck.class,
    TestPacket.class,
    TestRouteTable.class,
    TestTCP.class,
    TestHttpServer.class,
    TestStreamServerClient.class,
    TestConnTransfer.class,
    TestPrometheus.class,
    TestPromise.class,
    TestUtilities.class,

    AfterAll.class
})
public class VSuite {
}
