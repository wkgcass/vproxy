package io.vproxy.test;

import io.vproxy.test.cases.*;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

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
    TestHttp1Processor.class,
    TestHttp2Decoder.class,
    TestHealthCheck.class,
    TestPacket.class,
    TestPcap.class,
    TestRouteTable.class,
    TestTCP.class,
    TestHttpServer.class,
    TestHttpClient.class,
    TestNetServerClient.class,
    TestPrometheus.class,
    TestPromise.class,
    TestUtilities.class,
    TestByteArrayBuilder.class,
    TestSwitch.class,
    TestFlowParser.class,
    TestFlowGen.class,
    TestVPWSAgentConfig.class,
    TestUtils.class,
    TestIssues.class,

    AfterAll.class
})
public class VSuite {
}
