package io.vproxy.test.cases;

import io.vproxy.vproxyx.websocks.AutoProxy;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.Assert.*;

/** Matching fixtures are synthetic; the download test separately audits the live subscription. */
public class TestAutoProxy {
    private AutoProxy proxy(String rules) {
        var proxy = new AutoProxy("synthetic", false);
        proxy.addRule(rules);
        return proxy;
    }

    @Test
    public void defaultsAndMetadata() {
        var proxy = proxy("\uFEFF[AutoProxy 0.2.9]\r\n! comment\n \r");
        assertEquals("synthetic", proxy.getAutoProxySource());
        assertFalse(proxy.block("unlisted.example"));
        var blocked = new AutoProxy("synthetic", true);
        blocked.addRule("@@||allowed.example^");
        assertFalse(blocked.block("allowed.example"));
        assertTrue(blocked.block("unrelated-long-name.example"));
        assertTrue(blocked.block("notallowed.example"));
    }

    @Test
    public void wrappedBase64AndUtf8() {
        String text = "\uFEFF[AutoProxy 0.2.9]\r\n! synthetic\r||encoded.example^\n/资料/";
        String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        var proxy = proxy("");
        proxy.addBase64("\uFEFF \n" + encoded.replaceAll("(.{12})", "$1\r\n\t"));
        assertTrue(proxy.block("encoded.example"));
        assertTrue(proxy.block("资料.example"));
        assertFalse(proxy.block("https://documents.example/资料/index"));
        assertFalse(proxy.block("other.example"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void corruptBase64IsRejected() {
        proxy("").addBase64("YWJj#");
    }

    @Test
    public void domainAnchorAndSeparator() {
        var proxy = proxy("||anchor.example^");
        for (String input : new String[]{"anchor.example", "A.B.ANCHOR.EXAMPLE.",
            "https://anchor.example/path", "ftp://child.anchor.example:123/file",
            "https://user:pass@anchor.example/path"}) {
            assertTrue(input, proxy.block(input));
        }
        for (String input : new String[]{"notanchor.example", "anchor.example.evil",
            "https://safe.example/anchor.example", "https://safe.example/?next=anchor.example",
            "https://anchor.example@safe.example/", "https://safe.example/?next=http://anchor.example"}) {
            assertFalse(input, proxy.block(input));
        }
        assertTrue(proxy("||anchor.example").block("anchor.example.extra"));
    }

    @Test
    public void wildcardsAndAnchors() {
        var proxy = proxy("||edge*.wild.example^\n|https://*.nodes.example|");
        assertTrue(proxy.block("edge.wild.example"));
        assertTrue(proxy.block("edge42.wild.example"));
        assertTrue(proxy.block("child.edge42.wild.example"));
        assertFalse(proxy.block("notedge42.wild.example"));
        assertTrue(proxy.block("https://a.nodes.example/file"));
        assertTrue(proxy.block("http://a.nodes.example/file"));
        assertFalse(proxy.block("https://a.nodes.example.extra/file"));
        assertTrue(proxy("*needle*").block("has-needle.example"));
        assertTrue(proxy("ending|").block("https://host.ending/path"));
        assertFalse(proxy("ending|").block("https://other.example/ending"));
    }

    @Test
    public void urlsMatchOnlyHostOrIp() {
        var proxy = proxy("||exact.example^\n||192.0.2.7^\n||[2001:db8::7]^");
        for (String input : new String[]{"https://EXACT.example./area?q=one", "http://exact.example:8443/else",
            "ftp://user:pass@exact.example/file", "https://192.0.2.7:9443/path",
            "https://[2001:db8::7]:9443/path", "192.0.2.7", "2001:db8::7", "[2001:db8::7]"}) {
            assertTrue(input, proxy.block(input));
        }
        for (String input : new String[]{"https://other.example/?url=https://exact.example/",
            "https://exact.example@other.example/", "https://other.example/#192.0.2.7",
            "https://192.0.2.8/path", "https://[2001:db8::8]/path",
            "https:///exact.example", "https://[invalid]/"}) {
            assertFalse(input, proxy.block(input));
        }
    }

    @Test
    public void separatorsAndPlainSubstringRules() {
        assertTrue(proxy("marker^").block("marker"));
        for (String suffix : new String[]{"A", "4", "_", "-", ".extra", "%20"}) {
            assertFalse(suffix, proxy("marker^").block("marker" + suffix));
        }
        assertFalse(proxy("fragment").block("https://plain.example/some-FRAGMENT-here"));
        assertTrue(proxy("fragment").block("https://some-FRAGMENT-here.example/"));
        assertTrue(proxy(".dotted.example").block("child.dotted.example"));
        assertFalse(proxy(".dotted.example").block("dotted.example"));
        assertFalse(proxy(".dotted.example").block("notdotted.example"));
    }

    @Test
    public void exceptionsOverrideRegardlessOfOrderOrBatch() {
        for (boolean first : new boolean[]{false, true}) {
            var proxy = proxy(first ? "@@||free.policy.example^" : "||policy.example^");
            proxy.addRule(first ? "||policy.example^" : "@@||free.policy.example^");
            assertFalse(proxy.block("free.policy.example"));
            assertFalse(proxy.block("https://child.free.policy.example/private"));
            assertTrue(proxy.block("other.policy.example"));
            assertTrue(proxy.block("notfree.policy.example"));
        }
    }

    @Test
    public void allExceptionFormsAndShortRules() {
        String[] exceptions = {"@@x", "@@.permit.example", "@@||permit.example^",
            "@@|https://permit.example", "@@/permit\\.example/", "@@*permit*"};
        String[] inputs = {"x", "child.permit.example", "permit.example",
            "https://permit.example/file", "https://permit.example/file", "permit.example"};
        for (int i = 0; i < exceptions.length; ++i) {
            var proxy = proxy("*\n" + exceptions[i]);
            assertFalse(exceptions[i], proxy.block(inputs[i]));
            assertTrue(exceptions[i], proxy.block("unrelated.invalid"));
        }
        assertFalse(proxy("*\n@@||*.permit.example^").block("child.permit.example"));
        assertTrue(proxy("*\n@@||*.permit.example^").block("permit.example"));
    }

    @Test
    public void regularExpressionsUseSearchAndSupportLookahead() {
        var proxy = proxy("/item[0-9]+/\n/^https?:\\/\\/[^\\/]+\\.regex\\.example/\n"
            + "@@/^https?:\\/\\/(?=.*?(ab7|cd8))[a-z0-9.-]+\\.regex\\.example$/");
        assertFalse(proxy.block("https://other.example/prefix-item42-suffix"));
        assertTrue(proxy.block("https://prefix-item42-suffix.example/path"));
        assertTrue(proxy.block("one.regex.example"));
        assertFalse(proxy.block("ab7.regex.example"));
        assertFalse(proxy.block("https://cd8.regex.example/private"));
        assertTrue(proxy.block("xx.regex.example"));
        assertFalse(proxy.block("other.example"));
        assertFalse(proxy("/literal\\/slash/").block("https://other.example/literal/slash"));
    }

    @Test
    public void malformedRulesProduceDiagnostics() {
        var proxy = proxy("@@\n|\n||\n/[broken/\n||valid.example^\n||options.example^$script\n"
            + "bad.example##selector\n@@/^https?:\\/\\/ok\\.valid\\.example$");
        assertTrue(proxy.block("valid.example"));
        assertFalse(proxy.block("ok.valid.example"));
        assertFalse(proxy.block("unrelated.example"));
        assertFalse(proxy.block("options.example"));
        assertEquals(2, proxy.getRuleCount());
        var issues = proxy.getParseIssues();
        assertEquals(6, issues.stream().filter(i -> !i.recovered()).count());
        assertEquals(1, issues.stream().filter(AutoProxy.ParseIssue::recovered).count());
        assertTrue(issues.stream().anyMatch(i -> i.rule().equals("/[broken/") && i.reason().startsWith("invalid regexp:")));
        proxy.addRule("@@");
        assertEquals(7, issues.size()); // snapshot, not a mutable view
        assertEquals(8, proxy.getParseIssues().size());
    }

    @Test
    public void urlRulesApplyToTheEntireHost() {
        for (String rule : new String[]{"||scoped.example/private", "|https://scoped.example/private",
            "scoped.example/private", "||scoped.example/", "||scoped.example?key=value",
            "||scoped.example#part", "||scoped.example:9443", "/scoped\\.example\\/private/"}) {
            var proxy = proxy(rule);
            assertTrue(rule, proxy.block("scoped.example"));
            assertTrue(rule, proxy.block("https://scoped.example:9443/private?key=value#part"));
            assertTrue(rule, proxy.block("http://scoped.example/unrelated"));
            assertFalse(rule, proxy.block("unrelated.example"));
            assertFalse(rule, proxy.block("scoped.example.evil"));
        }
        var proxy = proxy("||scoped.example^\n@@||scoped.example/public");
        assertFalse(proxy.block("scoped.example"));
        assertFalse(proxy.block("https://scoped.example/public/page"));
        assertFalse(proxy.block("https://scoped.example/private/page"));
    }

    @Test
    public void urlProjectionPreservesWildcardsIpAndExceptions() {
        var proxy = proxy("||edge*.scoped.example/files/*|\n|ftp://exact.example/download|\n"
            + "||192.0.2.9:8080/private\n||[2001:db8::9]:8443/private");
        assertTrue(proxy.block("edge7.scoped.example"));
        assertTrue(proxy.block("child.edge7.scoped.example"));
        assertFalse(proxy.block("notedge7.scoped.example"));
        assertFalse(proxy.block("edge7.scoped.example.evil"));
        assertTrue(proxy.block("https://exact.example/anything"));
        assertFalse(proxy.block("child.exact.example"));
        assertTrue(proxy.block("192.0.2.9"));
        assertTrue(proxy.block("2001:db8::9"));
        assertTrue(proxy("|https://[2001:db8::10]:443/private").block("2001:db8::10"));
        assertFalse(proxy.block("192.0.2.90"));
        proxy.addRule("@@||edge7.scoped.example/only-this-path");
        assertFalse(proxy.block("https://edge7.scoped.example/another-path"));
        assertTrue(proxy.block("edge8.scoped.example"));
    }

    @Test
    public void regexpUrlsApplyToTheEntireHost() {
        var proxy = proxy("/^https?:\\/\\/(?:one|two)\\.regex\\.example\\/private(?:/.*)?$/\n"
            + "@@/^https?:\\/\\/one\\.regex\\.example\\/public$/");
        assertTrue(proxy.block("two.regex.example"));
        assertTrue(proxy.block("https://two.regex.example/unrelated"));
        assertFalse(proxy.block("one.regex.example"));
        assertFalse(proxy.block("two.regex.example.evil"));
        assertFalse(proxy.block("three.regex.example"));
        var alternatives = proxy("/^(?:one\\.branch\\.example/private|two\\.branch\\.example/public)$/");
        assertTrue(alternatives.block("one.branch.example"));
        assertTrue(alternatives.block("two.branch.example"));
        assertFalse(alternatives.block("three.branch.example"));
        assertTrue(proxy("/port\\.example:9443/private/").block("port.example"));
        assertTrue(proxy("/^ftp:\\/\\/files\\.example/download$/").block("https://files.example/other"));
    }

    @Test
    public void downloadAndParseGfwList() throws Exception {
        String source = "https://raw.githubusercontent.com/gfwlist/gfwlist/refs/heads/master/gfwlist.txt";
        var connection = (HttpURLConnection) URI.create(source).toURL().openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        String subscription;
        try {
            assertEquals("subscription HTTP status", 200, connection.getResponseCode());
            try (var stream = connection.getInputStream()) {
                subscription = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
        var proxy = new AutoProxy(source, false);
        proxy.addBase64(subscription);
        var rejected = proxy.getParseIssues().stream().filter(i -> !i.recovered()).toList();
        var recovered = proxy.getParseIssues().stream().filter(AutoProxy.ParseIssue::recovered).toList();
        var report = new StringBuilder("Source: ").append(source)
            .append("\nParsed rules: ").append(proxy.getRuleCount())
            .append("\nUnparseable rules: ").append(rejected.size()).append('\n');
        for (var issue : rejected) {
            report.append(issue.rule()).append("\n  Reason: ").append(issue.reason()).append('\n');
        }
        report.append("Recovered rules: ").append(recovered.size()).append('\n');
        for (var issue : recovered) {
            report.append(issue.rule()).append("\n  Reason: ").append(issue.reason()).append('\n');
        }
        report.append("All parsed rules participate in host matching; URL constraints apply to the entire host.\n");
        Path reportPath = Path.of("build", "reports", "gfwlist-parse.txt");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report);
        System.out.println(report);
        System.out.println("Report: " + reportPath.toAbsolutePath());
        assertTrue("subscription must contain rules", proxy.getRuleCount() > 0);
        assertTrue(report.toString(), rejected.isEmpty());
    }
}
