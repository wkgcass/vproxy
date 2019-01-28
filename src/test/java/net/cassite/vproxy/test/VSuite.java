package net.cassite.vproxy.test;

import net.cassite.vproxy.test.cases.TestNetMask;
import net.cassite.vproxy.test.cases.TestTcpLB;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    TestTcpLB.class,
    TestNetMask.class,
})
public class VSuite {
}
