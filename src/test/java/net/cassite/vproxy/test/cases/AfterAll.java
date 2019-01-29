package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.dns.Resolver;
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
