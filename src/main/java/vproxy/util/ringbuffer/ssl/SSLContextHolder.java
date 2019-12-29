package vproxy.util.ringbuffer.ssl;

import vproxy.util.Logger;

import javax.net.ssl.SSLContext;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SSLContextHolder {
    private static class Holder {
        final SSLContext sslContext;
        final CertHolder[] certs;

        private Holder(SSLContext sslContext, X509Certificate[] certs) {
            this.sslContext = sslContext;
            this.certs = new CertHolder[certs.length];
            for (int i = 0; i < certs.length; ++i) {
                this.certs[i] = new CertHolder(certs[i]);
            }
        }
    }

    private static class CertHolder {
        final X509Certificate cert;
        List<String> san;
        boolean sanRetrieved;

        private CertHolder(X509Certificate cert) {
            this.cert = cert;
        }
    }

    private final List<Holder> holders = new ArrayList<>();
    protected final Map<String, SSLContext> quickAccess = new ConcurrentHashMap<>();
    // quickAccess stores those SAN name without wildcard symbol to the corresponding SSLContext

    public void add(SSLContext sslContext, X509Certificate[] certs) {
        holders.add(new Holder(sslContext, certs));
    }

    public SSLContext choose(String sni) {
        assert Logger.lowLevelDebug("choosing cert with sni " + sni + ", holders.size = " + holders.size());
        if (holders.size() == 1) {
            return holders.get(0).sslContext;
        }
        if (holders.isEmpty()) {
            return null;
        }
        SSLContext ctx = chooseNoDefault(sni);
        if (ctx != null) {
            return ctx;
        }
        assert Logger.lowLevelDebug("cannot find corresponding cert for sni " + sni + ", return the default (first) one");
        return holders.get(0).sslContext;
    }

    protected SSLContext chooseNoDefault(String sni) {
        if (sni != null) {
            SSLContext ctx = quickAccess.get(sni);
            if (ctx != null) {
                return ctx;
            }
            for (Holder h : holders) {
                if (checkSNI(h, h.certs, sni)) {
                    return h.sslContext;
                }
            }
        }
        return null;
    }

    private boolean checkSNI(Holder holder, CertHolder[] certs, String sni) {
        assert Logger.lowLevelDebug("visiting certs: with " + certs.length + " element(s)");
        for (CertHolder cert : certs) {
            if (checkSNI(holder, cert, sni)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkSNI(Holder holder, CertHolder cert, String sni) {
        List<String> sanStrList;
        // san result example:
        // [[2, *.pixiv.net], [2, pixiv.net], [2, *.pixiv.org], [2, pixiv.org], [2, *.pximg.net], [2, pximg.net], [2, *.ads-pixiv.net], [2, ads-pixiv.net]]
        // [[2, *.youtube.com], [2, youtube.com], [2, *.ytimg.com], [2, ytimg.com], [2, *.ggpht.com], [2, ggpht.com], [2, *.googlevideo.com], [2, googlevideo.com], [2, *.googleapis.com], [2, googleapis.com], [2, *.googlesyndication.com], [2, googlesyndication.com]]
        // [[2, *.google.com], [2, google.com], [2, *.google.com.hk], [2, google.com.hk]]
        if (!cert.sanRetrieved) {
            Collection<List<?>> san;
            try {
                san = cert.cert.getSubjectAlternativeNames();
            } catch (CertificateParsingException e) {
                assert Logger.lowLevelDebug("decoding cert SAN failed: " + e);
                cert.sanRetrieved = true;
                return false;
            }
            List<String> ls = new ArrayList<>(san.size());
            for (List<?> o : san) {
                int n = (Integer) o.get(0);
                if (n == 2) {
                    String dnsName = (String) o.get(1);
                    ls.add(dnsName);
                }
            }
            if (ls.isEmpty()) {
                ls = null;
            }
            sanStrList = ls;
            cert.san = ls;
            cert.sanRetrieved = true;
        } else {
            sanStrList = cert.san;
        }
        if (sanStrList == null) {
            assert Logger.lowLevelDebug("san is not retrieved");
            return false;
        } else {
            assert Logger.lowLevelDebug("retrieved san is " + sanStrList);
        }
        for (var dnsName : sanStrList) {
            assert Logger.lowLevelDebug("comparing sni " + sni + " with dnsName " + dnsName);
            if (dnsName.startsWith("*.")) {
                // wildcard record
                String suffix = dnsName.substring("*.".length());
                if (sni.length() > suffix.length()) {
                    if (sni.endsWith(suffix)) {
                        String prefix = sni.substring(0, sni.length() - suffix.length() - 1 /*also remove the `dot`*/);
                        if (!prefix.contains(".")) {
                            quickAccess.put(dnsName, holder.sslContext);
                            return true;
                        }
                    }
                }
            } else {
                // plain
                if (sni.equals(dnsName)) {
                    quickAccess.put(dnsName, holder.sslContext);
                    return true;
                }
            }
        }
        return false;
    }
}
