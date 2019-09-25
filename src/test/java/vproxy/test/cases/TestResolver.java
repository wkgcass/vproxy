package vproxy.test.cases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vproxy.dns.Resolver;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.BlockCallback;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestResolver {
    private SelectorEventLoop loop;
    private Resolver resolver;

    @Before
    public void setUp() throws IOException {
        loop = SelectorEventLoop.open();
        resolver = new Resolver("TestResolver" + ((int) (Math.random() * 10000)));
        resolver.start();
    }

    @After
    public void tearDown() throws IOException {
        resolver.stop();
        loop.close();
    }

    @Test
    public void resolve() throws Exception {
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        InetAddress address = cb.block();
        assertEquals("127.0.0.1", Utils.ipStr(address.getAddress()));

        List<Resolver.Cache> cacheList = new LinkedList<>();
        resolver.copyCache(cacheList);
        assertEquals(1, cacheList.size());
        assertEquals("localhost", cacheList.get(0).host);
    }

    @Test
    public void resolveIpv6() throws Exception {
        BlockCallback<Inet6Address, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolveV6("localhost", cb);
        InetAddress address = cb.block();
        assertEquals("[0000:0000:0000:0000:0000:0000:0000:0001]", Utils.ipStr(address.getAddress()));
    }

    @Test
    public void resolveCache() throws Exception {
        resolver.ttl = 2000;
        // let's resolve
        BlockCallback<InetAddress, UnknownHostException> cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        InetAddress address = cb.block();

        // check
        assertEquals("127.0.0.1", Utils.ipStr(address.getAddress()));
        assertEquals("cache count should be 1 because only one resolve", 1, resolver.cacheCount());

        // wait for > 2000 ms for cache to expire
        Thread.sleep(2500);
        assertEquals("there should be no cache now", 0, resolver.cacheCount());

        // resolve again
        cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        address = cb.block();
        assertEquals("127.0.0.1", Utils.ipStr(address.getAddress()));

        assertEquals("now should be 1 cache because resolved again", 1, resolver.cacheCount());

        // resolve again
        cb = new BlockCallback<>();
        resolver.resolve("localhost", cb);
        address = cb.block();
        assertEquals("127.0.0.1", Utils.ipStr(address.getAddress()));

        assertEquals("should still be 1 cache because already cached", 1, resolver.cacheCount());
    }
}
