package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.dns.Resolver;
import org.junit.BeforeClass;
import org.junit.Test;

import java.security.Security;

public class BeforeAll {
    @BeforeClass
    public static void classSetUp() {
        Security.setProperty("networkaddress.cache.ttl", "0");
        Resolver.getDefault();
    }

    @Test
    public void beforeAll() {
    }
}
