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
    TestTcpReset.class,
    TestPerformanceRegressions.class,
    TestRingBufferHandlerLifecycle.class,
    TestReadOnlyFD.class,
    TestDatagramDeferredConfig.class,
    TestDatagramLifecycle.class,
    TestSendingQueueCursor.class,
    TestSendingQueueAge.class,
    TestReceivingQueueRetention.class,
    TestTunReadFairness.class,
    TestTunReadDiagnostics.class,
    TestTunWriteDiagnostics.class,
    TestEventLoopDiagnostics.class,
    TestConnectionReadFairness.class,
    TestConnectionReadBackpressure.class,
    TestDecoderErrorNotification.class,
    TestHttpServer.class,
    TestHttpClient.class,
    TestNetServerClient.class,
    TestPrometheus.class,
    TestPromise.class,
    TestUtilities.class,
    TestByteArrayBuilder.class,
    TestSwitch.class,
    io.vproxy.vswitch.iface.TestXdpPacketCopy.class,
    TestFlowParser.class,
    TestFlowGen.class,
    TestVPWSAgentConfig.class,
    TestH2StreamedEcho.class,
    TestArqStreamedLifecycle.class,
    TestDirectRelayAllocation.class,
    TestRelayConnectLifecycle.class,
    TestAgentConnectLifecycle.class,
    TestConnectionPoolLifecycle.class,
    TestAutoProxy.class,
    TestUtils.class,
    TestCommand.class,
    TestIssues.class,

    AfterAll.class
})
public class VSuite {
}
