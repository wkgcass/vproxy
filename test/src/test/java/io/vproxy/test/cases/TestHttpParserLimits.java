package io.vproxy.test.cases;

import io.vproxy.base.http.HttpReqParser;
import io.vproxy.base.http.HttpRespParser;
import io.vproxy.base.util.codec.AbstractParser;
import io.vproxy.base.util.nio.ByteArrayChannel;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class TestHttpParserLimits {
    static final String LINE = "GET / HTTP/1.1\r\n";
    static final String FIELD = "X: a\r\n";
    static int feed(AbstractParser<?> parser, String text, int block) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        int rc = -1;
        for (int i = 0; i < bytes.length; i += block) {
            var channel = ByteArrayChannel.fromFull(java.util.Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + block)));
            rc = parser.feed(channel);
            if (parser.getErrorMessage() != null) return rc;
        }
        return rc;
    }
    static HttpReqParser.Params limits() {
        return new HttpReqParser.Params().setHeadersOnly(true).setMaxInitialLineBytes(64)
            .setMaxHeaderLineBytes(32).setMaxHeaderBytes(128).setMaxHeaderCount(3);
    }
    static void invalidLimit(Runnable operation) {
        try { operation.run(); fail("negative limit accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void exactBudgetsIncludeDelimitersAcrossEveryByteBoundary() {
        String wire = LINE + FIELD + "\r\n";
        for (int block : new int[]{1, 2, 5, 128}) {
            var p = new HttpReqParser(limits().setMaxInitialLineBytes(LINE.length())
                .setMaxHeaderLineBytes(FIELD.length()).setMaxHeaderBytes(wire.length()).setMaxHeaderCount(1));
            assertEquals(0, feed(p, wire, block));
            assertNull(p.getErrorMessage());
            assertEquals("a", p.getResult().headers.getFirst().value);
        }
    }
    @Test public void initialLineRejectsBeforeAppendingAnExtraByte() {
        var p = new HttpReqParser(limits().setMaxInitialLineBytes(LINE.length() - 1));
        assertEquals(-1, feed(p, LINE + "\r\n", 1));
        assertEquals(414, p.getErrorStatusCode());
        assertTrue(p.getErrorMessage().contains("initial line"));
    }
    @Test public void fieldBudgetCountsCarriageReturnsThatTheParserDiscards() {
        var p = new HttpReqParser(limits().setMaxHeaderLineBytes(10));
        assertEquals(-1, feed(p, LINE + "X: " + "\r".repeat(30) + "\n\r\n", 1));
        assertEquals(431, p.getErrorStatusCode());
        assertTrue(p.getErrorMessage().contains("header line"));
    }
    @Test public void totalBudgetIncludesInitialLineAndFinalEmptyLine() {
        String wire = LINE + FIELD + "\r\n";
        var p = new HttpReqParser(limits().setMaxHeaderBytes(wire.length() - 1));
        assertEquals(-1, feed(p, wire, 2));
        assertEquals(431, p.getErrorStatusCode());
        assertTrue(p.getErrorMessage().contains("header section"));
    }
    @Test public void fieldCountAppliesToDuplicateNames() {
        var p = new HttpReqParser(limits());
        assertEquals(-1, feed(p, LINE + FIELD.repeat(4) + "\r\n", 1));
        assertTrue(p.getErrorMessage().contains("header count"));
        assertEquals(431, p.getErrorStatusCode());
    }
    @Test public void largeFieldFailsWithBoundedConstantErrorText() {
        var p = new HttpReqParser(limits());
        assertEquals(-1, feed(p, LINE + "X: " + "a".repeat(65536) + "\r\n\r\n", 4096));
        assertTrue(p.getErrorMessage().length() < 80);
    }
    @Test public void budgetsResetBetweenMessagesOnTheSameParser() {
        var p = new HttpReqParser(limits().setMaxHeaderCount(1).setMaxHeaderBytes(24));
        for (int i = 0; i < 8; i++) assertEquals(0, feed(p, LINE + FIELD + "\r\n", 1));
        assertNull(p.getErrorMessage());
    }
    @Test public void parametersAreCopiedAndNegativeLimitsRejected() {
        var params = limits();
        var p = new HttpReqParser(params);
        params.maxHeaderBytes = 1;
        assertEquals(0, feed(p, LINE + FIELD + "\r\n", 1));
        invalidLimit(() -> limits().setMaxInitialLineBytes(-1));
        invalidLimit(() -> limits().setMaxHeaderLineBytes(-1));
        invalidLimit(() -> limits().setMaxHeaderBytes(-1));
        invalidLimit(() -> limits().setMaxHeaderCount(-1));
        params.maxHeaderCount = -1;
        invalidLimit(() -> new HttpReqParser(params));
    }
    @Test public void fullBodyParsingDoesNotCountBodyAgainstHeaderBudget() {
        String body = "z".repeat(8192);
        String head = "POST / HTTP/1.1\r\nContent-Length: 8192\r\n\r\n";
        var p = new HttpReqParser(limits().setHeadersOnly(false).setMaxHeaderBytes(head.length()));
        assertEquals(0, feed(p, head + body, 1));
        assertEquals(8192, p.getResult().body.length());
        assertEquals('z', p.getResult().body.get(8191));
    }
    @Test public void segmentedParsingEnforcesHeadersWithoutLimitingTheBody() {
        String head = "POST / HTTP/1.1\r\nContent-Length: 256\r\n\r\n";
        var p = new HttpReqParser(limits().setHeadersOnly(false).setSegmentedParsing(true).setBuildResult(false)
            .setMaxHeaderBytes(head.length()));
        assertEquals(0, feed(p, head, 1));
        assertEquals(256, p.getBuilder().dataLength);
        // Segmented mode delegates body consumption to its caller, which then advances state.
        assertEquals(10, p.nextState());
        assertEquals(0, p.nextState());
        assertNull(p.getErrorMessage());
        assertEquals(0, feed(p, LINE + FIELD + "\r\n", 1));
        var bad = new HttpReqParser(limits().setSegmentedParsing(true).setMaxHeaderCount(1));
        assertEquals(-1, feed(bad, LINE + FIELD.repeat(2) + "\r\n", 1));
    }
    @Test public void responseParserHonorsTheSameBudgets() {
        String head = "HTTP/1.1 200 OK\r\n" + FIELD + "\r\n";
        var params = new HttpRespParser.Params().setHeadersOnly(true).setMaxInitialLineBytes(17)
            .setMaxHeaderLineBytes(FIELD.length()).setMaxHeaderBytes(head.length()).setMaxHeaderCount(1);
        assertEquals(0, feed(new HttpRespParser(params), head, 1));
        var bad = new HttpRespParser(params.setMaxHeaderCount(1));
        assertEquals(-1, feed(bad, "HTTP/1.1 200 OK\r\n" + FIELD.repeat(2) + "\r\n", 1));
        assertNotNull(bad.getErrorMessage());
    }
    @Test public void unrestrictedParserDefaultsRemainCompatible() {
        String value = "a".repeat(65536);
        var p = new HttpReqParser(new HttpReqParser.Params().setHeadersOnly(true));
        assertEquals(0, feed(p, LINE + "X: " + value + "\r\n\r\n", 4096));
        assertEquals(value, p.getResult().headers.getFirst().value);
    }
}
