package vproxy.test.cases;

import vproxy.util.Utils;
import org.junit.Test;

import java.net.Inet6Address;
import java.net.InetAddress;

import static org.junit.Assert.*;

public class TestIpParser {
    @Test
    public void parseIpv4() {
        int a = 192;
        int b = 168;
        int c = 12;
        int d = 34;
        String s = a + "." + b + "." + c + "." + d;
        byte[] bytes = Utils.parseIpv4String(s);
        assert bytes != null;
        assertEquals((byte) a, bytes[0]);
        assertEquals((byte) b, bytes[1]);
        assertEquals((byte) c, bytes[2]);
        assertEquals((byte) d, bytes[3]);

        a = 192;
        b = 168;
        c = 0;
        d = 0;
        s = a + "." + b + "." + c + "." + d;
        bytes = Utils.parseIpv4String(s);
        assert bytes != null;
        assertEquals((byte) a, bytes[0]);
        assertEquals((byte) b, bytes[1]);
        assertEquals((byte) c, bytes[2]);
        assertEquals((byte) d, bytes[3]);
    }

    @Test
    public void parseIpv4Fail() {
        assertNull(Utils.parseIpv4String("1"));
        assertNull(Utils.parseIpv4String("a.b.c.d"));
        assertNull(Utils.parseIpv4String("1.2.3."));
        assertNull(Utils.parseIpv4String("..."));
        assertNull(Utils.parseIpv4String("1.2.."));
        assertNull(Utils.parseIpv4String("..3.4"));
        assertNull(Utils.parseIpv4String("1...4"));
        assertNull(Utils.parseIpv4String("256.1.1.1"));
        assertNull(Utils.parseIpv4String("1.256.1.1"));
    }

    private void check(String s) throws Exception {
        InetAddress addr = InetAddress.getByName(s);
        byte[] b1 = addr.getAddress();
        byte[] b2 = Utils.parseIpv6String(s);
        assert b2 != null;
        if (addr instanceof Inet6Address) {
            assertArrayEquals(b1, b2);
        } else {
            for (int i = 0; i < 10; ++i) {
                assertEquals(0, b2[i]);
            }
            for (int i = 11; i < 12; ++i) {
                assertEquals((byte) 0xFF, b2[i]);
            }
            for (int i = 0; i < 4; ++i) {
                assertEquals(b1[i], b2[i + 12]);
            }
        }
        if (s.startsWith("[")) {
            return;
        }
        check("[" + s + "]");
    }

    @Test
    public void parseIpv6() throws Exception {
        // https://tools.ietf.org/html/rfc4291#section-2.2
        check("ABCD:EF01:2345:6789:ABCD:EF01:2345:6789");
        check("2001:DB8:0:0:8:800:200C:417A");
        check("FF01:0:0:0:0:0:0:101");
        check("0:0:0:0:0:0:0:1");
        check("0:0:0:0:0:0:0:0");
        check("2001:DB8::8:800:200C:417A");
        check("FF01::101");
        check("::1");
        check("::");
        check("0:0:0:0:0:0:13.1.68.3");
        check("0:0:0:0:0:FFFF:129.144.52.38");
        check("::13.1.68.3");
        check("::FFFF:129.144.52.38");

        // my test cases
        check("1::");
        check("1a2b::3c4d");
        check("22::");
        check("333::");
        check("4444::");

        // https://stackoverflow.com/questions/10995273/test-case-for-validation-ipv4-embedded-ipv6-addresses
        check("2001:db8:c000:221::");
        check("2001:db8:1c0:2:21::");
        check("2001:db8:122:c000:2:2100::");
        check("2001:db8:122:3c0:0:221::");
        check("2001:db8:122:344:c0:2:2100::");
        check("2001:db8:122:344::192.0.2.33");
    }

    // from guava
    // https://github.com/google/guava/blob/master/guava-tests/test/com/google/common/net/InetAddressesTest.java#L42
    // Copyright (C) 2008 The Guava Authors
    // apache2 license
    String[] bogusInputs = {
        "",
        "016.016.016.016",
        "016.016.016",
        "016.016",
        "016",
        "000.000.000.000",
        "000",
        "0x0a.0x0a.0x0a.0x0a",
        "0x0a.0x0a.0x0a",
        "0x0a.0x0a",
        "0x0a",
        "42.42.42.42.42",
        "42.42.42",
        "42.42",
        "42",
        "42..42.42",
        "42..42.42.42",
        "42.42.42.42.",
        "42.42.42.42...",
        ".42.42.42.42",
        "...42.42.42.42",
        "42.42.42.-0",
        "42.42.42.+0",
        ".",
        "...",
        "bogus",
        "bogus.com",
        "192.168.0.1.com",
        "12345.67899.-54321.-98765",
        "257.0.0.0",
        "42.42.42.-42",
        "3ffe::1.net",
        "3ffe::1::1",
        "1::2::3::4:5",
        "::7:6:5:4:3:2:", // should end with ":0"
        ":6:5:4:3:2:1::", // should begin with "0:"
        "2001::db:::1",
        "FEDC:9878",
        "+1.+2.+3.4",
        "1.2.3.4e0",
        "::7:6:5:4:3:2:1:0", // too many parts
        "7:6:5:4:3:2:1:0::", // too many parts
        "9:8:7:6:5:4:3::2:1", // too many parts
        "0:1:2:3::4:5:6:7", // :: must remove at least one 0.
        "3ffe:0:0:0:0:0:0:0:1", // too many parts (9 instead of 8)
        "3ffe::10000", // hextet exceeds 16 bits
        "3ffe::goog",
        "3ffe::-0",
        "3ffe::+0",
        "3ffe::-1",
        ":",
        ":::",
        "::1.2.3",
        "::1.2.3.4.5",
        "::1.2.3.4:",
        "1.2.3.4::",
        "2001:db8::1:",
        ":2001:db8::1",
        ":1:2:3:4:5:6:7",
        "1:2:3:4:5:6:7:",
        ":1:2:3:4:5:6:"
    };

    @Test
    public void parseIpFail() {
        for (String ip : bogusInputs) {
            assertNull(Utils.parseIpString(ip));
        }
    }
}
