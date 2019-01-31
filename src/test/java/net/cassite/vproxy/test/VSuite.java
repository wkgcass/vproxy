package net.cassite.vproxy.test;

import net.cassite.vproxy.test.cases.*;
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

    AfterAll.class
})
public class VSuite {
}
