package io.vproxy.test.cases;

import io.vproxy.base.processor.http1.entity.Header;
import io.vproxy.vproxyx.websocks.WebSocksUtils;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class TestWebSocketUpgradeHeaders {
    static final String KEY = "dGhlIHNhbXBsZSBub25jZQ==";
    static List<Header> headers(String upgrade, String connection, String key) {
        return new ArrayList<>(List.of(new Header("Upgrade", upgrade), new Header("Connection", connection),
            new Header("Sec-WebSocket-Key", key)));
    }
    @Test public void asciiCaseAndCommaSeparatedTokensAreAccepted() {
        assertTrue(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers("WebSocket", "keep-alive, Upgrade", KEY), false));
        assertTrue(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers("h2c, WEBSOCKET", "keep-alive, uPgRaDe", KEY), false));
    }
    @Test public void tokensMustMatchCompletelyAndUseAsciiCaseRules() {
        for (String token : new String[]{"upgrader", "xupgrade", "up grade"})
            assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers("websocket", token, KEY), false));
        for (String token : new String[]{"websockets", "xwebsocket", "websoc\u212aet"})
            assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers(token, "Upgrade", KEY), false));
    }
    @Test public void serverKeyMustDecodeToExactlySixteenBytes() {
        for (int count : new int[]{0, 1, 15, 17, 32}) {
            String key = java.util.Base64.getEncoder().encodeToString(new byte[count]);
            assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers("websocket", "Upgrade", key), false));
        }
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers("websocket", "Upgrade", "!bad!"), false));
        assertTrue(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers("websocket", "Upgrade", KEY), false));
    }
    @Test public void duplicateKeysCannotHideAfterAnEarlySuccessfulMatch() {
        var list = headers("websocket", "Upgrade", KEY);
        list.add(new Header("Sec-WebSocket-Key", "YQ=="));
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(list, false));
        list.set(3, new Header("Sec-WebSocket-Key", KEY));
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(list, false));
        java.util.Collections.reverse(list);
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(list, false));
    }
    @Test public void splitConnectionFieldsAreCombinedWithoutOrderDependence() {
        for (int i = 0; i < 12; i++) {
            var list = headers("websocket", "keep-alive", KEY);
            list.add(new Header("Connection", "Upgrade"));
            java.util.Collections.shuffle(list, new java.util.Random(i));
            assertTrue(WebSocksUtils.checkUpgradeToWebSocketHeaders(list, false));
        }
    }
    @Test public void clientStillChecksTheExactExpectedAcceptValue() {
        var list = List.of(new Header("Upgrade", "WebSocket"), new Header("Connection", "keep-alive, Upgrade"),
            new Header("Sec-WebSocket-Accept", "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="));
        assertTrue(WebSocksUtils.checkUpgradeToWebSocketHeaders(list, true));
        var bad = new ArrayList<>(list);
        bad.set(2, new Header("Sec-WebSocket-Accept", "wrong"));
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(bad, true));
        bad.set(2, list.get(2)); bad.add(list.get(2));
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(bad, true));
    }
    @Test public void absentTokensAndExistingBodyPolicyRemainRejected() {
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers("h2c", "Upgrade", KEY), false));
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(headers("websocket", "keep-alive", KEY), false));
        var list = headers("websocket", "Upgrade", KEY);
        list.add(new Header("Content-Length", "0"));
        assertFalse(WebSocksUtils.checkUpgradeToWebSocketHeaders(list, false));
    }
}
