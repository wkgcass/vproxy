package vproxy.test;

import vproxy.test.cases.*;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    BeforeAll.class,

    TestTcpLB.class,
    TestNetMask.class,
    TestTimer.class,
    TestResolver.class,
    TestSocks5.class,
    TestDiscovery.class,
    TestKhala.class,
    TestSmart.class,
    TestConnectClient.class,
    TestSSL.class,
    TestProtocols.class,
    TestHttp1Parser.class,

    AfterAll.class
})
public class VSuite {
}
