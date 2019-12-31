package vproxy.test.cases;

import org.junit.BeforeClass;
import org.junit.Test;
import vproxy.dns.Resolver;

public class BeforeAll {
    @BeforeClass
    public static void classSetUp() {
        Resolver.getDefault();
    }

    @Test
    public void beforeAll() {
    }
}
