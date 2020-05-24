package vproxyx.websocks;

import vproxybase.util.LogType;
import vproxybase.util.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * https://github.com/gfwlist/gfwlist/wiki/Syntax
 */
public class ABP {
    private final Set<Character> validSimpleRuleStart = new HashSet<>() {{
        for (int i = 'a'; i <= 'z'; ++i) {
            add((char) i);
        }
        for (int i = 'A'; i <= 'Z'; ++i) {
            add((char) i);
        }
        for (int i = '0'; i <= '9'; ++i) {
            add((char) i);
        }
        add('.');
    }};
    private final boolean defaultBlock;
    private final List<Function<String, Boolean>> checkers = new LinkedList<>();

    public ABP(boolean defaultBlock) {
        this.defaultBlock = defaultBlock;
    }

    public boolean block(String input) {
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
        addRule(new String(Base64.getDecoder().decode(base64)));
    }

    public void addRule(String rule) {
        for (String line : rule.split("\n")) {
            addRuleOneLine(line);
        }
    }

    private void addRuleOneLine(String line) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) {
            // empty line or comment
            return;
        }
        if (line.startsWith("||")) {
            addMatchingSpecificURI(line.substring("||".length()));
        } else if (line.startsWith("|")) {
            addMatchingFromBeginning(line.substring("|".length()));
        } else if (line.startsWith("/") && line.endsWith("/")) {
            addMatchingRegexp(line.substring("/".length(), line.length() - "/".length()));
        } else if (line.startsWith("@@||")) {
            addWhitelistRuleMatchingSpecificURI(line.substring("@@||".length()));
        } else if (line.startsWith("@@|")) {
            addWhitelistRuleMatchingFromBeginning(line.substring("@@|".length()));
        } else if (line.startsWith("@@")) {
            addWhitelistSimpleRule(line.substring("@@".length()));
        } else if (line.startsWith("@@/") && line.endsWith("/")) {
            addWhitelistRuleRegexp(line.substring("@@/".length(), line.length() - "/".length()));
        } else if (validSimpleRuleStart.contains(line.charAt(0))) {
            addSimpleRule(line);
        } else {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "Unrecognized ABP rule: " + line);
        }
    }

    private interface MatchingSpecificURI extends Function<String, Boolean> {
    }

    private void addMatchingSpecificURI(String rule) {
        var host = extractHost(rule);
        var addDot = "." + host;
        checkers.add((MatchingSpecificURI) input -> {
            if (input.equals(host) || (input.endsWith(addDot) && input.length() > addDot.length())) {
                Logger.alert(input + " matches ABP matching specific uri rule: " + rule);
                return true;
            }
            return null;
        });
    }

    private interface MatchingFromBeginning extends Function<String, Boolean> {
    }

    private void addMatchingFromBeginning(String rule) {
        var host = extractHost(rule);
        checkers.add((MatchingFromBeginning) input -> {
            if (input.equals(host)) {
                Logger.alert(input + " matches ABP matching from beginning rule: " + rule);
                return true;
            }
            return null;
        });
    }

    private interface MatchingRegexp extends Function<String, Boolean> {
    }

    private void addMatchingRegexp(String rule) {
        Pattern pattern = Pattern.compile(rule);
        checkers.add((MatchingRegexp) input -> {
            String[] protocols = new String[]{"", "http://", "https://"};
            for (String protocol : protocols) {
                if (pattern.matcher(protocol + input).matches()) {
                    Logger.alert(input + " matches ABP matching regexp rule: " + rule);
                    return true;
                }
            }
            return null;
        });
    }

    private interface WhitelistRuleMatchingSpecificURI extends Function<String, Boolean> {
    }

    private void addWhitelistRuleMatchingSpecificURI(String rule) {
        var host = extractHost(rule);
        var addDot = "." + host;
        checkers.add((WhitelistRuleMatchingSpecificURI) input -> {
            if (input.equals(host) || (input.endsWith(addDot) || input.length() > addDot.length())) {
                assert Logger.lowLevelDebug(input + " matches ABP WHITELIST matching specific uri rule: " + rule);
                return false;
            }
            return null;
        });
    }

    private interface WhitelistMatchingFromBeginningRule extends Function<String, Boolean> {
    }

    private void addWhitelistRuleMatchingFromBeginning(String rule) {
        var host = extractHost(rule);
        checkers.add((WhitelistMatchingFromBeginningRule) input -> {
            if (input.startsWith(host)) {
                assert Logger.lowLevelDebug(input + " matches ABP WHITELIST matching from beginning rule: " + rule);
                return false;
            }
            return null;
        });
    }

    private interface WhitelistSimpleRule extends Function<String, Boolean> {
    }

    private void addWhitelistSimpleRule(String rule) {
        var host = extractHost(rule);
        checkers.add((WhitelistSimpleRule) input -> {
            if (input.contains(host)) {
                assert Logger.lowLevelDebug(input + " matches ABP WHITELIST simple rule: " + rule);
                return false;
            }
            return null;
        });
    }

    private interface WhitelistRegexpRule extends Function<String, Boolean> {
    }

    private void addWhitelistRuleRegexp(String rule) {
        Pattern pattern = Pattern.compile(rule);
        checkers.add((WhitelistRegexpRule) input -> {
            String[] protocols = new String[]{"", "http://", "https://"};
            for (String protocol : protocols) {
                if (pattern.matcher(protocol + input).matches()) {
                    Logger.alert(input + " matches ABP WHITELIST regexp rule: " + rule);
                    return false;
                }
            }
            return null;
        });
    }

    private interface SimpleRule extends Function<String, Boolean> {
    }

    private void addSimpleRule(String rule) {
        var host = extractHost(rule);
        checkers.add((SimpleRule) input -> {
            if (input.contains(host)) {
                Logger.alert(input + " matches ABP simple rule: " + rule);
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
        // remove url
        if (uri.contains("/")) {
            uri = uri.substring(0, uri.indexOf("/"));
        }
        return uri;
    }
}
