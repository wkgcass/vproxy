package vclient;

import javax.net.ssl.SSLContext;

public class GeneralClientOptions<REAL extends GeneralClientOptions<REAL>> {
    public int timeout;
    public SSLContext sslContext;
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

    public REAL setTimeout(int timeout) {
        this.timeout = timeout;
        return toReal();
    }

    public REAL setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return toReal();
    }

    public GeneralClientOptions<REAL> setClientContext(ClientContext clientContext) {
        this.clientContext = clientContext;
        return this;
    }
}
