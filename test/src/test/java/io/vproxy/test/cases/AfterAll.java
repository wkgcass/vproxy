package io.vproxy.test.cases;

import org.junit.AfterClass;
import org.junit.Test;
import io.vproxy.base.dns.Resolver;

public class AfterAll {
    @AfterClass
    public static void classTearDown() {
        Resolver.stopDefault();
    }

    @Test
    public void afterAll() {
    }
}
