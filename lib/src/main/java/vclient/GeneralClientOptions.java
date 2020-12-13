package vclient;

import javax.net.ssl.SSLContext;

public class GeneralClientOptions<REAL extends GeneralClientOptions<REAL>> {
    public int timeout;
    public SSLContext sslContext;
    public String host;
    public ClientContext clientContext = new ClientContext(null);

    public GeneralClientOptions() {
        this.timeout = 10_000;
        this.sslContext = null;
    }

    public GeneralClientOptions(REAL that) {
        this.timeout = that.timeout;
        this.sslContext = that.sslContext;
    }

    private REAL toReal() {
        //noinspection unchecked
        return (REAL) this;
    }

    public REAL fill(GeneralClientOptions<?> opts) {
        this.timeout = opts.timeout;
        this.sslContext = opts.sslContext;
        this.host = opts.host;
        this.clientContext = opts.clientContext;
        return toReal();
    }

    public REAL setTimeout(int timeout) {
        this.timeout = timeout;
        return toReal();
    }

    public REAL setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return toReal();
    }

    public REAL setHost(String host) {
        this.host = host;
        return toReal();
    }

    public REAL setClientContext(ClientContext clientContext) {
        this.clientContext = clientContext;
        return toReal();
    }
}
