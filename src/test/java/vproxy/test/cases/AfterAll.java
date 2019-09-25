package vproxy.test.cases;

import org.junit.AfterClass;
import org.junit.Test;
import vproxy.dns.Resolver;

public class AfterAll {
    @AfterClass
    public static void classTearDown() {
        Resolver.stopDefault();
    }

    @Test
    public void afterAll() {
    }
}
