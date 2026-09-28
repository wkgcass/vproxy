package io.vproxy.vproxyx.websocks;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AutoProxy {
    private final String autoProxySource;
    private final boolean defaultBlock;
    // Insert exceptions at the front so they always override blocking rules.
    private final List<Function<String, Boolean>> checkers = new LinkedList<>();

    private final List<ParseIssue> parseIssues = new ArrayList<>();

    public record ParseIssue(String rule, String reason, boolean recovered) {
    }

    public List<ParseIssue> getParseIssues() {
        return List.copyOf(parseIssues);
    }

    public int getRuleCount() {
        return checkers.size();
    }

    public AutoProxy(String autoProxySource, boolean defaultBlock) {
        this.autoProxySource = autoProxySource;
        this.defaultBlock = defaultBlock;
    }

    public String getAutoProxySource() {
        return autoProxySource;
    }

    public boolean block(String input) {
        // Both URL inputs and URL filters apply to the entire host.
        if (input.contains("://")) {
            try {
                input = URI.create(input).getHost();
            } catch (IllegalArgumentException e) {
                return defaultBlock;
            }
            if (input == null) {
                return defaultBlock;
            }
        }
        input = input.toLowerCase(Locale.ROOT);
        if (input.endsWith(".")) {
            input = input.substring(0, input.length() - 1);
        }
        if (input.contains(":") && !input.startsWith("[")) {
            input = "[" + input + "]";
        }
        for (var func : checkers) {
            Boolean result = func.apply(input);
            if (result == null) { // null means don't know
                continue;
            }
            return result;
        }
        return defaultBlock;
    }

    public void addBase64(String base64) {
        addRule(new String(Base64.getDecoder().decode(base64.replace("\uFEFF", "").replaceAll("\\s", "")), StandardCharsets.UTF_8));
    }

    public void addRule(String rule) {
        for (String line : rule.split("\\r\\n|\\r|\\n")) {
            try {
                addRuleOneLine(line);
            } catch (PatternSyntaxException e) {
                invalid(line.trim(), "invalid regexp: " + e.getDescription());
            }
        }
    }

    private void addRuleOneLine(String line) {
        line = line.replace("\uFEFF", "").trim();
        if (line.isEmpty() || line.startsWith("!") || line.matches("\\[AutoProxy[^]]*]")) {
            // empty line or comment
            return;
        }
        String original = line;
        String body = line.startsWith("@@") ? line.substring(2) : line;
        if (body.isEmpty() || body.equals("|") || body.equals("||")) {
            invalid(original, "empty filter");
            return;
        }
        boolean recovered = body.startsWith("/^") && !body.endsWith("/");
        if (recovered) {
            line += "/";
        } else if (!(body.startsWith("/") && body.endsWith("/"))
            && (body.contains("$") || body.contains("##") || body.contains("#@#"))) {
            invalid(original, "unsupported browser filter options");
            return;
        }
        if (line.startsWith("||") && line.length() > "||".length()) {
            addMatchingSpecificURI(line.substring("||".length()));
        } else if (line.startsWith("|") && line.length() > "|".length()) {
            addMatchingFromBeginning(line.substring("|".length()));
        } else if (line.startsWith("/") && line.endsWith("/") && line.length() > 2) {
            addMatchingRegexp(line.substring("/".length(), line.length() - "/".length()));
        } else if (line.startsWith("@@||") && line.length() > "@@||".length()) {
            addWhitelistRuleMatchingSpecificURI(line.substring("@@||".length()));
        } else if (line.startsWith("@@|") && line.length() > "@@|".length()) {
            addWhitelistRuleMatchingFromBeginning(line.substring("@@|".length()));
        } else if (line.startsWith("@@/") && line.endsWith("/") && line.length() > 4) {
            addWhitelistRuleRegexp(line.substring("@@/".length(), line.length() - "/".length()));
        } else if (line.startsWith("@@.") && line.length() > "@@.".length()) {
            addWhitelistSuffixRule(line.substring("@@.".length()));
        } else if (line.startsWith("@@") && line.length() > "@@".length()) {
            addWhitelistSimpleRule(line.substring("@@".length()));
        } else if (line.startsWith(".") && line.length() > ".".length()) {
            addSuffixRule(line.substring(".".length()));
        } else {
            addSimpleRule(line);
        }
        if (recovered) {
            String reason = "missing closing '/' in regexp; interpreting as regexp";
            parseIssues.add(new ParseIssue(original, reason, true));
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, reason + ": " + original);
        }
    }

    private interface MatchingSpecificURI extends Function<String, Boolean> {
    }

    private void addMatchingSpecificURI(String rule) {
        var pattern = compileFilter("||" + rule);
        checkers.add((MatchingSpecificURI) input -> {
            if (pattern.matcher(input).find()) {
                Logger.alert(input + " matches auto proxy matching specific uri rule: " + rule);
                return true;
            }
            return null;
        });
    }

    private interface MatchingFromBeginning extends Function<String, Boolean> {
    }

    private void addMatchingFromBeginning(String rule) {
        var pattern = compileFilter("|" + rule);
        checkers.add((MatchingFromBeginning) input -> {
            if (pattern.matcher(input).find()) {
                Logger.alert(input + " matches auto proxy matching from beginning rule: " + rule);
                return true;
            }
            return null;
        });
    }

    private interface MatchingRegexp extends Function<String, Boolean> {
    }

    private void addMatchingRegexp(String rule) {
        Pattern pattern = compileHostRegexp(rule);
        checkers.add((MatchingRegexp) input -> {
            String[] protocols = new String[]{"", "http://", "https://"};
            for (String protocol : protocols) {
                if (pattern.matcher(protocol + input).find()) {
                    Logger.alert(input + " matches auto proxy matching regexp rule: " + rule);
                    return true;
                }
            }
            return null;
        });
    }

    private interface WhitelistRuleMatchingSpecificURI extends Function<String, Boolean> {
    }

    private void addWhitelistRuleMatchingSpecificURI(String rule) {
        var pattern = compileFilter("||" + rule);
        checkers.add(0, (WhitelistRuleMatchingSpecificURI) input -> {
            if (pattern.matcher(input).find()) {
                assert Logger.lowLevelDebug(input + " matches auto proxy WHITELIST matching specific uri rule: " + rule);
                return false;
            }
            return null;
        });
    }

    private interface WhitelistMatchingFromBeginningRule extends Function<String, Boolean> {
    }

    private void addWhitelistRuleMatchingFromBeginning(String rule) {
        var pattern = compileFilter("|" + rule);
        checkers.add(0, (WhitelistMatchingFromBeginningRule) input -> {
            if (pattern.matcher(input).find()) {
                assert Logger.lowLevelDebug(input + " matches auto proxy WHITELIST matching from beginning rule: " + rule);
                return false;
            }
            return null;
        });
    }

    private interface WhitelistSimpleRule extends Function<String, Boolean> {
    }

    private void addWhitelistSimpleRule(String rule) {
        var pattern = compileFilter("" + rule);
        checkers.add(0, (WhitelistSimpleRule) input -> {
            if (pattern.matcher(input).find()) {
                assert Logger.lowLevelDebug(input + " matches auto proxy WHITELIST simple rule: " + rule);
                return false;
            }
            return null;
        });
    }

    private interface WhitelistRegexpRule extends Function<String, Boolean> {
    }

    private void addWhitelistRuleRegexp(String rule) {
        Pattern pattern = compileHostRegexp(rule);
        checkers.add(0, (WhitelistRegexpRule) input -> {
            String[] protocols = new String[]{"", "http://", "https://"};
            for (String protocol : protocols) {
                if (pattern.matcher(protocol + input).find()) {
                    Logger.alert(input + " matches auto proxy WHITELIST regexp rule: " + rule);
                    return false;
                }
            }
            return null;
        });
    }

    private interface WhitelistSuffixRule extends Function<String, Boolean> {
    }

    private void addWhitelistSuffixRule(String rule) {
        var pattern = compileFilter("." + rule);
        checkers.add(0, (WhitelistSuffixRule) input -> {
            if (pattern.matcher(input).find()) {
                Logger.alert(input + " matches auto proxy WHITELIST suffix rule: " + rule);
                return false;
            }
            return null;
        });
    }

    private interface SuffixRule extends Function<String, Boolean> {
    }

    private void addSuffixRule(String rule) {
        var pattern = compileFilter("." + rule);
        checkers.add((SuffixRule) input -> {
            if (pattern.matcher(input).find()) {
                Logger.alert(input + " matches auto proxy suffix rule: " + rule);
                return true;
            }
            return null;
        });
    }

    private interface SimpleRule extends Function<String, Boolean> {
    }

    private void addSimpleRule(String rule) {
        var pattern = compileFilter("" + rule);
        checkers.add((SimpleRule) input -> {
            if (pattern.matcher(input).find()) {
                Logger.alert(input + " matches auto proxy simple rule: " + rule);
                return true;
            }
            return null;
        });
    }

    private String extractHost(String uri) {
        // remove protocol
        if (uri.contains("://")) {
            uri = uri.substring(uri.indexOf("://") + "://".length());
        }
        // Drop URL constraints, retaining the host boundary they implied.
        int start = uri.startsWith("[") ? uri.indexOf(']') + 1 : 0;
        for (int i = start; i < uri.length(); ++i) {
            if ("/?#:".indexOf(uri.charAt(i)) >= 0) {
                return i == 0 ? "*" : uri.substring(0, i) + "^";
            }
        }
        return uri;
    }

    private void invalid(String rule, String reason) {
        parseIssues.add(new ParseIssue(rule, reason, false));
        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "Invalid auto proxy rule (" + reason + "): " + rule);
    }

    private Pattern compileFilter(String rule) {
        String anchor = rule.startsWith("||") ? "||" : rule.startsWith("|") ? "|" : "";
        String host = extractHost(rule.substring(anchor.length()));
        StringBuilder regex = new StringBuilder(anchor.equals("||") ? "(?:^|\\.)" : anchor.equals("|") ? "^" : "");
        boolean end = host.endsWith("|");
        if (end) {
            host = host.substring(0, host.length() - 1);
        }
        for (char c : host.toCharArray()) {
            regex.append(c == '*' ? ".*" : c == '^' ? "(?:[^a-zA-Z0-9_\\-.%]|$)" : Pattern.quote(String.valueOf(c)));
        }
        if (end) {
            regex.append('$');
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static Pattern compileHostRegexp(String regexp) {
        // Validate the original too: truncating an invalid path must not hide errors.
        Pattern.compile(regexp, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        boolean inClass = false;
        int depth = 0;
        int skipDepth = -1;
        StringBuilder projected = new StringBuilder();
        for (int i = 0; i < regexp.length(); ++i) {
            char c = regexp.charAt(i);
            int tokenStart = i;
            boolean escaped = c == '\\' && i + 1 < regexp.length();
            if (escaped) {
                c = regexp.charAt(++i);
            }
            if (!escaped && c == '[') {
                inClass = true;
            } else if (!escaped && c == ']') {
                inClass = false;
            }
            if (inClass || (!escaped && c == ']')) {
                if (skipDepth < 0) {
                    projected.append(regexp, tokenStart, i + 1);
                }
                continue;
            }
            if (!escaped && c == '(') {
                ++depth;
            } else if (!escaped && c == ')') {
                --depth;
            }
            if (skipDepth >= 0) {
                if (!escaped && c == '|' && depth == skipDepth) {
                    projected.append('|');
                    skipDepth = -1;
                } else if (!escaped && c == ')' && depth < skipDepth) {
                    projected.append(')');
                    skipDepth = depth;
                }
                continue;
            }
            if (c == '/' && tokenStart > 0 && regexp.charAt(tokenStart - 1) == ':') {
                // Keep the two slashes of :// (including their escaped form).
                int next = i + 1;
                int schemeEnd = -1;
                if (regexp.startsWith("\\/", next)) {
                    schemeEnd = next + 2;
                } else if (regexp.startsWith("/", next)) {
                    schemeEnd = next + 1;
                }
                if (schemeEnd >= 0) {
                    if (depth == 0) {
                        // The URL scheme is irrelevant to host routing.
                        projected.setLength(0);
                        projected.append('^');
                    } else {
                        projected.append(regexp, tokenStart, schemeEnd);
                    }
                    i = schemeEnd - 1;
                    continue;
                }
            }
            boolean port = c == ':' && i + 1 < regexp.length()
                && (Character.isDigit(regexp.charAt(i + 1)) || regexp.startsWith("\\d", i + 1)
                || regexp.startsWith("[0-9]", i + 1));
            if (c == '/' || (escaped && c == '?') || c == '#' || port) {
                projected.append("(?=[^a-zA-Z0-9_\\-.%]|$)");
                // Discard this URL tail, retaining sibling alternatives and
                // closing groups from the host part of the expression.
                skipDepth = depth;
            } else {
                projected.append(regexp, tokenStart, i + 1);
            }
        }
        return Pattern.compile(projected.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
