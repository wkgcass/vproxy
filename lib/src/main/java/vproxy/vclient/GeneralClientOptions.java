package vproxy.vclient;

public class GeneralClientOptions<REAL extends GeneralClientOptions<REAL>> {
    public int timeout = 10_000;
    public ClientContext clientContext = new ClientContext(null);

    public GeneralClientOptions() {
    }

    public GeneralClientOptions(REAL that) {
        this.timeout = that.timeout;
        this.clientContext = that.clientContext;
    }

    protected REAL toReal() {
        //noinspection unchecked
        return (REAL) this;
    }

    public REAL fill(GeneralClientOptions<?> opts) {
        this.timeout = opts.timeout;
        this.clientContext = opts.clientContext;
        return toReal();
    }

    public REAL setTimeout(int timeout) {
        this.timeout = timeout;
        return toReal();
    }

    public REAL setClientContext(ClientContext clientContext) {
        this.clientContext = clientContext;
        return toReal();
    }
}
