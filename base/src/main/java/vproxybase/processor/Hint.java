package vproxybase.processor;

import vfd.IP;
import vproxybase.util.AnnotationKeys;

import java.util.Map;

public class Hint {
    private final String host;
    private final String port;
    private final String uri;

    private String formatHost(String s) {
        if (s == null) {
            return null;
        }
        int colonIndex = s.indexOf(':');
        if (IP.isIpv6(s) || colonIndex == -1) {
            return s;
        }
        s = s.substring(0, colonIndex);
        if (s.startsWith("www.")) {
            s = s.substring("www.".length());
        }
        if (s.isEmpty()) {
            return null;
        }
        return s;
    }

    public Hint(String host) {
        this.host = formatHost(host);
        this.port = null;
        this.uri = null;
    }

    public Hint(String host, int port) {
        this.host = formatHost(host);
        this.port = "" + port;
        this.uri = null;
    }

    private String formatUri(String s) {
        if (s == null) {
            return null;
        }
        int questionIndex = s.indexOf('?');
        if (questionIndex != -1) {
            s = s.substring(0, questionIndex);
        }
        if (s.equals("/")) {
            return "/";
        }
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public Hint(String host, String uri) {
        this.host = formatHost(host);
        this.port = null;
        this.uri = formatUri(uri);
    }

    public Hint(String host, int port, String uri) {
        this.host = formatHost(host);
        this.port = "" + port;
        this.uri = formatUri(uri);
    }

    public static final int MAX_MATCH_LEVEL = 3;
    private static final int HOST_EXACT_MATCH = 2048;
    private static final int HOST_SUFFIX_MATCH = 1024;
    private static final int URI_MAX_MATCH = 1023;

    @SuppressWarnings("unchecked")
    public int matchLevel(Map<String, String>... annotations) {
        if (annotations == null) {
            return 0;
        }
        String annoHost = null;
        String annoPort = null;
        String annoUri = null;

        for (Map<String, String> a : annotations) {
            if (a == null) {
                continue;
            }
            if (annoHost == null) {
                annoHost = a.get(AnnotationKeys.ServerGroup_HintHost);
            }
            if (annoPort == null) {
                annoPort = a.get(AnnotationKeys.ServerGroup_HintPort);
            }
            if (annoUri == null) {
                annoUri = a.get(AnnotationKeys.ServerGroup_HintUri);
            }
        }

        if (annoHost == null && annoPort == null && annoUri == null) {
            return 0;
        }

        if (port != null && annoPort != null) {
            if (!this.port.equals(annoPort)) { // port not matched, so nothing matches
                return 0;
            }
        }

        int level = 0;
        if (annoHost != null && this.host != null) {
            if (this.host.equals(annoHost)) { // exact match
                level += HOST_EXACT_MATCH;
            } else if (this.host.endsWith("." + annoHost)) { // input value is a sub domain name of the hint
                level += HOST_SUFFIX_MATCH;
            }
        }
        int uriLevel = 0;
        if (annoUri != null && this.uri != null) {
            if (this.uri.equals(annoUri)) {
                uriLevel = this.uri.length();
            } else if (this.uri.startsWith(annoUri)) {
                uriLevel = annoUri.length();
            }
        }
        if (uriLevel > URI_MAX_MATCH) {
            uriLevel = URI_MAX_MATCH;
        }
        level += uriLevel;

        return level;
    }

    @Override
    public String toString() {
        return "Hint{" +
            "host=" + host +
            ", port=" + port +
            ", uri=" + uri +
            '}';
    }
}
