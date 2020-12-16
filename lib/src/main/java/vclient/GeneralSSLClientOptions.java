package vclient;

import vproxybase.util.ringbuffer.SSLUtils;

import javax.net.ssl.SSLContext;

public class GeneralSSLClientOptions<REAL extends GeneralSSLClientOptions<REAL>> extends GeneralClientOptions<REAL> {
    public SSLContext sslContext = null;
    public String host = null;
    public String[] alpn = null;

    public GeneralSSLClientOptions() {
    }

    public GeneralSSLClientOptions(REAL that) {
        super(that);
        this.sslContext = that.sslContext;
        this.host = that.host;
    }

    public REAL fill(GeneralSSLClientOptions<?> opts) {
        super.fill(opts);
        this.sslContext = opts.sslContext;
        this.host = opts.host;
        return toReal();
    }

    public REAL setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return toReal();
    }

    public REAL setSSL(boolean b) {
        if (b) {
            this.sslContext = SSLUtils.getDefaultClientSSLContext();
        } else {
            this.sslContext = null;
        }
        return toReal();
    }

    public REAL setHost(String host) {
        this.host = host;
        return toReal();
    }

    public REAL setAlpn(String[] alpn) {
        this.alpn = alpn;
        return toReal();
    }
}
