package io.vproxy.base.processor;

import io.vproxy.base.util.Annotations;
import io.vproxy.vfd.IP;

public class Hint {
    private final String host;
    private final int port;
    private final String uri;

    private Hint(String host, int port, String uri) {
        this.host = host;
        this.port = port;
        this.uri = uri;
    }

    public static Hint ofHost(String host) {
        return new Hint(
            formatHost(host),
            0,
            null
        );
    }

    public static Hint ofHostPort(String host, int port) {
        return new Hint(
            formatHost(host),
            port,
            null
        );
    }

    public static Hint ofHostUri(String host, String uri) {
        return new Hint(
            formatHost(host),
            0,
            formatUri(uri)
        );
    }

    public static Hint ofHostPortUri(String host, int port, String uri) {
        return new Hint(
            formatHost(host),
            port,
            formatUri(uri)
        );
    }

    public static Hint ofUri(String uri) {
        return new Hint(
            null,
            0,
            formatUri(uri)
        );
    }

    private static String formatHost(String s) {
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

    private static String formatUri(String s) {
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

    private static final int HOST_SHIFT = 10;
    private static final int HOST_EXACT_MATCH = 3;
    private static final int HOST_SUFFIX_MATCH = 2;
    private static final int HOST_WILDCARD_MATCH = 1;
    private static final int URI_SHIFT = 0;
    private static final int URI_MAX_MATCH = 1023;
    private static final int URI_WILDCARD_MATCH = 1;

    public int matchLevel(Annotations... annosArray) {
        if (annosArray == null) {
            return 0;
        }
        String annoHost = null;
        int annoPort = 0;
        String annoUri = null;

        for (Annotations a : annosArray) {
            if (annoHost == null) {
                annoHost = a.ServerGroup_HintHost;
            }
            if (annoPort == 0) {
                annoPort = a.ServerGroup_HintPort;
            }
            if (annoUri == null) {
                annoUri = a.ServerGroup_HintUri;
            }
        }

        if (annoHost == null && annoPort == 0 && annoUri == null) {
            return 0;
        }

        if (port != 0 && annoPort != 0) {
            if (this.port != annoPort) { // port not matched, so nothing matches
                return 0;
            }
        }

        int level = 0;

        int hostLevel = 0;
        if (annoHost != null && this.host != null) {
            if (this.host.equals(annoHost)) { // exact match
                hostLevel = HOST_EXACT_MATCH;
            } else if (this.host.endsWith("." + annoHost)) { // input value is a sub domain name of the hint
                hostLevel = HOST_SUFFIX_MATCH;
            } else if (annoHost.equals("*")) { // the annotation is a wildcard
                hostLevel = HOST_WILDCARD_MATCH;
            }
        }
        level += hostLevel << HOST_SHIFT;

        int uriLevel = 0;
        if (annoUri != null && this.uri != null) {
            if (this.uri.equals(annoUri)) {
                uriLevel = this.uri.length() + URI_WILDCARD_MATCH;
            } else if (this.uri.startsWith(annoUri)) {
                uriLevel = annoUri.length() + URI_WILDCARD_MATCH;
            } else if (annoUri.equals("*")) {
                uriLevel = URI_WILDCARD_MATCH;
            }
        }
        if (uriLevel > URI_MAX_MATCH) {
            uriLevel = URI_MAX_MATCH;
        }
        level += uriLevel << URI_SHIFT;

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
