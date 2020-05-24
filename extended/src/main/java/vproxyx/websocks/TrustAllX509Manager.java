package vproxyx.websocks;

import vproxybase.util.LogType;
import vproxybase.util.Logger;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

class TrustAllX509Manager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] certs, String autoType) {
        Logger.warn(LogType.ALERT, "NO CLIENT CERT CHECK");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType) {
        Logger.warn(LogType.ALERT, "NO SERVER CERT CHECK");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
