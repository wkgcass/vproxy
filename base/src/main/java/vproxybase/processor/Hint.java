package vproxybase.processor;

import vfd.IP;
import vproxybase.util.AnnotationKeys;

import java.util.Map;

public class Hint {
    public final String hint;

    private final String host;
    private final String port;

    public Hint(String hint) {
        this.hint = hint;
        if (IP.isIpv6(hint) || !hint.contains(":")) {
            // consider as hostname or ip
            host = hint;
            port = null;
        } else {
            // consider as (hostname/ip):port
            host = hint.substring(0, hint.lastIndexOf(':'));
            port = hint.substring(hint.lastIndexOf(':') + 1);
        }
    }

    public static final int MAX_MATCH_LEVEL = 3;

    @SuppressWarnings("unchecked")
    public int matchLevel(Map<String, String>... annotations) {
        if (annotations == null) {
            return 0;
        }
        String annoHost = null;
        String annoPort = null;

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
        }

        if (annoHost == null && annoPort == null) {
            return 0;
        }
        if (annoHost == null) { // for now, we do not support to determine from annotations without `host`
            return 0;
        }

        if (port != null && annoPort != null) {
            if (!this.port.equals(annoPort)) { // port not matched, so nothing matches
                return 0;
            }
        }
        if (this.host.equals(annoHost)) { // exact match
            return 3;
        }
        if (this.host.endsWith("." + annoHost)) { // input value is a sub domain name of the hint
            return 2;
        }
        if (annoHost.endsWith("." + this.host)) { // hint is a sub domain name of input value
            return 1;
        }
        return 0; // not matched
    }

    @Override
    public String toString() {
        return "Hint{" +
            "hint=" + hint +
            '}';
    }
}
