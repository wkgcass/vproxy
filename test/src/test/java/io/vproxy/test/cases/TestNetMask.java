package io.vproxy.test.cases;

import io.vproxy.base.util.Network;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.vfd.IP;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TestNetMask {
    @Test
    public void mask() {
        for (int m = 0; m <= 128; ++m) {
            byte[] bytes = Network.parseMask(m);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int positive = Utils.positive(b);
                String bin = Integer.toBinaryString(positive);
                int len = 8 - bin.length();
                sb.append("0".repeat(Math.max(0, len)));
                sb.append(bin);
            }
            String binSeriesFromByte = sb.toString();
            sb.delete(0, sb.length());
            sb.append("1".repeat(m));
            if (m <= 32) {
                sb.append("0".repeat(32 - m));
            } else {
                sb.append("0".repeat(128 - m));
            }
            String binSeriesFromMask = sb.toString();

            assertEquals(binSeriesFromMask, binSeriesFromByte);
        }
    }

    @Test
    public void validNetwork() {
        List<Tuple<Boolean, Tuple<String, Integer>>> list = Arrays.asList(
            new Tuple<>(false/**/, new Tuple<>("10.144.0.0", 11)),
            new Tuple<>(true/*-*/, new Tuple<>("10.144.0.0", 12)),
            new Tuple<>(true/*-*/, new Tuple<>("10.144.0.0", 13)),
            new Tuple<>(false/**/, new Tuple<>("[0000:0010:0000:0000:0000:0000:0000:0000]", 27)),
            new Tuple<>(true/*-*/, new Tuple<>("[0000:0010:0000:0000:0000:0000:0000:0000]", 28)),
            new Tuple<>(true/*-*/, new Tuple<>("[0000:0010:0000:0000:0000:0000:0000:0000]", 29)),
            new Tuple<>(false/**/, new Tuple<>("[0000:0010:0000:0000:1000:0000:0000:0000]", 67)),
            new Tuple<>(true/*-*/, new Tuple<>("[0000:0010:0000:0000:1000:0000:0000:0000]", 68)),
            new Tuple<>(true/*-*/, new Tuple<>("[0000:0010:0000:0000:1000:0000:0000:0000]", 69))
        );
        for (Tuple<Boolean, Tuple<String, Integer>> tup : list) {
            boolean b = tup.left;
            String addr = tup.right.left;
            int mask = tup.right.right;
            byte[] bip = IP.parseIpString(addr);
            byte[] bmask = Network.parseMask(mask);
            assertEquals("check for " + tup, b, Network.validNetwork(bip, bmask));
        }
    }

    @Test
    public void ipNetMask() {
        List<Tuple<Boolean, Tuple<String, String>>> list = Arrays.asList(
            // true:  input v4, rule v4, mask v4
            new Tuple<>(true, new Tuple<>("10.144.0.1", "10.144.0.0/12")),
            // true:  input v4, rule v4, mask v4
            new Tuple<>(true, new Tuple<>("10.144.0.1", "10.144.0.0/13")),
            // true:  input v4, rule v4, mask v4
            new Tuple<>(true, new Tuple<>("10.152.0.1", "10.144.0.0/12")),
            // true: input v4, rule v6, mask v6, for IPv4-Compatible IPv6 address
            new Tuple<>(true, new Tuple<>("127.0.0.1", "[0000:0000:0000:0000:0000:0000:7F00:0000]/112")),
            // true: input v4, rule v6, mask v6, for IPv4-Mapped IPv6 address
            new Tuple<>(true, new Tuple<>("127.0.0.1", "[0000:0000:0000:0000:0000:ffff:7F00:0000]/112")),
            // true: input v6, rule v4, mask v4, for IPv4-Compatible IPv6 address
            new Tuple<>(true, new Tuple<>("[0000:0000:0000:0000:0000:0000:7F00:0001]", "127.0.0.1/32")),
            // true: input v6, rule v4, mask v4, for IPv4-Mapped IPv6 address
            new Tuple<>(true, new Tuple<>("[0000:0000:0000:0000:0000:FFFF:7F00:0001]", "127.0.0.1/32")),
            // false: input v4, rule v4, mask v4
            new Tuple<>(false, new Tuple<>("10.152.0.1", "10.144.0.0/13")),
            // false: input v4, rule v6, mask v4
            new Tuple<>(false, new Tuple<>("255.255.255.255", "[0000:0010:0000:0000:0000:0000:0000:0000]/28")),
            // false: input v4, rule v6, mask v4
            new Tuple<>(false, new Tuple<>("127.0.0.1", "[0000:0010:0000:0000:0000:0000:0000:0000]/28")),
            // false: input v4, rule v6, mask v6, for not match
            new Tuple<>(false, new Tuple<>("128.0.0.1", "[0000:0000:0000:0000:0000:0000:7F00:0000]/112")),
            // false: input v4, rule v6, mask v6, for IPv4-Mapped IPv6 address
            new Tuple<>(false, new Tuple<>("128.0.0.1", "[0000:0000:0000:0000:0000:ffff:7F00:0000]/112")),
            // false: input v6, rule v4, mask v4, for not Compatible nor Mapped
            new Tuple<>(false, new Tuple<>("[0000:0000:0000:0000:0000:1234:7F00:0001]", "127.0.0.1/32")),
            // false: input v6, rule v4, mask v4, for not match
            new Tuple<>(false, new Tuple<>("[0000:0000:0000:0000:0000:FFFF:7F00:0002]", "127.0.0.1/32"))
        );
        for (Tuple<Boolean, Tuple<String, String>> tup : list) {
            boolean b = tup.left;
            String input = tup.right.left;
            String net = tup.right.right;
            byte[] binput = IP.parseIpString(input);
            byte[] baddr = IP.parseIpString(net.split("/")[0]);
            byte[] bmask = Network.parseMask(Integer.parseInt(net.split("/")[1]));
            assertEquals("match for " + tup, b, Network.maskMatch(binput, baddr, bmask));
        }
    }

    private void assertNetworkContains(String network, String ip) {
        assertTrue(Network.from(network).contains(IP.from(ip)));
    }

    @Test
    public void networkContains() {
        assertNetworkContains("192.168.1.2/32", "192.168.1.2");
        assertNetworkContains("192.168.1.2/32", "::ffff:192.168.1.2");
        assertNetworkContains("192.168.1.2/32", "::192.168.1.2");
        assertNetworkContains("192.168.1.0/24", "192.168.1.2");
        assertNetworkContains("192.168.1.0/24", "::ffff:192.168.1.2");
        assertNetworkContains("192.168.1.0/24", "::192.168.1.2");

        assertNetworkContains("::ffff:192.168.1.0/120", "192.168.1.2");
        assertNetworkContains("::192.168.1.0/120", "192.168.1.2");

        assertNetworkContains("fd00::2/128", "fd00::2");
        assertNetworkContains("fd00::1:0:0/97", "fd00::1:0:2");
        assertNetworkContains("fd00::1:0:0/96", "fd00::1:0:2");
        assertNetworkContains("fd00::1:0:0:0:0/65", "fd00::1:0:0:0:2");
        assertNetworkContains("fd00::1:0:0:0:0/64", "fd00::1:0:0:0:2");
        assertNetworkContains("fd00:1::/33", "fd00:1::2");
        assertNetworkContains("fd00:1::/32", "fd00:1::2");
        assertNetworkContains("fd00::/8", "fd00::2");
        assertNetworkContains("::/0", "abcd:ef01:9988::1234");
    }

    private void assertNetworkNotContains(String network, String ip) {
        assertFalse(Network.from(network).contains(IP.from(ip)));
    }

    @Test
    public void networkNotContains() {
        assertNetworkNotContains("192.168.1.0/24", "192.168.2.1");
        assertNetworkNotContains("192.168.1.0/24", "::ffff:192.168.2.1");
        assertNetworkNotContains("192.168.1.0/24", "::192.168.2.1");
        assertNetworkNotContains("192.168.1.0/24", "2001::192.168.1.1");

        assertNetworkNotContains("fd00::2/128", "fd00::1");
        assertNetworkNotContains("fd00::1:0:0/97", "fd00::2:0:2");
        assertNetworkNotContains("fd00::1:0:0/96", "fd00::2:0:2");
        assertNetworkNotContains("fd00::1:0:0:0:0/65", "fd00::2:0:0:0:2");
        assertNetworkNotContains("fd00::1:0:0:0:0/64", "fd00::2:0:0:0:2");
        assertNetworkNotContains("fd00:1::/33", "fd00:2::2");
        assertNetworkNotContains("fd00:1::/32", "fd00:2::2");
        assertNetworkNotContains("fd00::/8", "fe00::2");
    }
}
