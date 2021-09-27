package io.vproxy.test.cases;

import io.vproxy.base.dns.Resolver;
import org.junit.BeforeClass;
import org.junit.Test;

public class BeforeAll {
    @BeforeClass
    public static void classSetUp() {
        Resolver.getDefault();
    }

    @Test
    public void beforeAll() {
    }
}
