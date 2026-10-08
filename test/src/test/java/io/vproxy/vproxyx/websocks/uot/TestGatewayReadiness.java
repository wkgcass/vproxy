package io.vproxy.vproxyx.websocks.uot;

import io.vproxy.base.util.Network;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class TestGatewayReadiness {
    @Before
    public void requiresPosixShell() {
        assumeFalse(OS.isWindows());
    }

    private static class Fixture implements AutoCloseable {
        final Path probes = Files.createTempFile("gateway-probes-", ".txt");
        final String gateway;
        final Network network;
        final String route;
        final Function<String, String> ping;
        final Function<String, String> neighbor;

        Fixture(boolean ipv6, int readyAfter) throws Exception {
            gateway = ipv6 ? "fd00:250::1" : "10.250.0.1";
            network = Network.from(ipv6 ? "fd00:250::/64" : "10.250.0.0/24");
            route = "printf '%s\\n' " + quote("destination via " + gateway + " dev test");
            ping = ip -> "printf x >> " + quote(probes.toString());
            neighbor = ip -> "if [ $(wc -c < " + quote(probes.toString()) + ") -ge " + readyAfter
                + " ]; then printf '%s\\n' " + quote(gateway + " dev test lladdr 02:00:00:00:00:01 REACHABLE")
                + "; else printf '%s\\n' " + quote(gateway + " dev test INCOMPLETE") + "; fi";
        }

        int probes() throws Exception {
            return Files.readAllBytes(probes).length;
        }

        public void close() throws Exception {
            Files.delete(probes);
        }
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    @SuppressWarnings("unchecked")
    private static Tuple<MacAddress, IP> resolve(Fixture f, String route, Function<String, String> neighbor) throws Exception {
        var method = UdpOverTcpSetup.class.getDeclaredMethod("getIPvXGW", Network.class, String.class, Function.class, Function.class);
        method.setAccessible(true);
        try {
            return (Tuple<MacAddress, IP>) method.invoke(null, f.network, route, f.ping, neighbor);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw e;
        }
    }

    @Test
    public void delayedIpv4AndIpv6NeighborsBecomeUsable() throws Exception {
        for (boolean ipv6 : new boolean[]{false, true}) {
            for (int readyAfter : new int[]{2, 3}) {
                try (var f = new Fixture(ipv6, readyAfter)) {
                    var result = resolve(f, f.route, f.neighbor);
                    assertEquals(IP.from(f.gateway), result.right);
                    assertEquals(new MacAddress("02:00:00:00:00:01"), result.left);
                    assertEquals(readyAfter, f.probes());
                }
            }
        }
    }

    @Test
    public void readyNeighborNeedsOnlyOneProbe() throws Exception {
        for (boolean ipv6 : new boolean[]{false, true}) {
            try (var f = new Fixture(ipv6, 1)) {
                assertEquals(IP.from(f.gateway), resolve(f, f.route, f.neighbor).right);
                assertEquals(1, f.probes());
            }
        }
    }

    @Test
    public void unavailableNeighborHasBoundedRetriesAndFinalDiagnostic() throws Exception {
        for (boolean ipv6 : new boolean[]{false, true}) {
            try (var f = new Fixture(ipv6, 4)) {
                try {
                    resolve(f, f.route, f.neighbor);
                    fail("unavailable neighbor must fail");
                } catch (Exception e) {
                    assertTrue(e.getMessage(), e.getMessage().contains("gateway mac not found"));
                    assertTrue(e.getMessage(), e.getMessage().contains("INCOMPLETE"));
                }
                assertEquals(3, f.probes());
            }
        }
    }

    @Test
    public void routeFailuresDoNotProbeNeighbors() throws Exception {
        try (var f = new Fixture(false, 1)) {
            for (String route : new String[]{"printf route-failed >&2; exit 7", "printf 'destination dev test'", "printf 'destination via 10.251.0.1 dev test'"}) {
                try {
                    resolve(f, route, f.neighbor);
                    fail("invalid route must fail");
                } catch (Exception expected) {
                    assertFalse(expected.getMessage().isBlank());
                }
                assertEquals(0, f.probes());
            }
        }
    }

    @Test
    public void neighborCommandFailureIsNotHiddenByRetries() throws Exception {
        try (var f = new Fixture(false, 1)) {
            try {
                resolve(f, f.route, ip -> "printf neighbor-query-failed >&2; exit 9");
                fail("neighbor command failure must fail");
            } catch (Exception e) {
                assertTrue(e.getMessage(), e.getMessage().contains("neighbor-query-failed"));
            }
            assertEquals(1, f.probes());
        }
    }

    @Test
    public void whitespaceInRouteAndNeighborOutputIsAccepted() throws Exception {
        try (var f = new Fixture(false, 1)) {
            var result = resolve(f, "printf ' destination\\tvia  10.250.0.1 dev test\\n'",
                ip -> "printf '10.250.0.1\\tdev test\\tlladdr\\t02:00:00:00:00:01 REACHABLE\\n'");
            assertEquals(IP.from(f.gateway), result.right);
            assertEquals(new MacAddress("02:00:00:00:00:01"), result.left);
            assertEquals(1, f.probes());
        }
    }

    @Test
    public void malformedNeighborOutputFailsImmediately() throws Exception {
        try (var f = new Fixture(false, 1)) {
            try {
                resolve(f, f.route, ip -> "printf '10.250.0.1 dev test lladdr'");
                fail("missing mac token must fail");
            } catch (Exception e) {
                assertTrue(e.getMessage(), e.getMessage().contains("invalid output"));
            }
            assertEquals(1, f.probes());
        }
    }
}
