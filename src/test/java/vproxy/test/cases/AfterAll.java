package vproxy.test.cases;

import vproxy.dns.Resolver;
import org.junit.AfterClass;
import org.junit.Test;

public class AfterAll {
    @AfterClass
    public static void classTearDown() {
        Resolver.stopDefault();
    }

    @Test
    public void afterAll() {
    }
}
