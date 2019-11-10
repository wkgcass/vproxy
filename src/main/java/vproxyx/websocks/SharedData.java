package vproxyx.websocks;

public class SharedData {
    public final boolean useSSL;
    public final boolean useKCP;

    public SharedData(boolean useSSL, boolean useKCP) {
        this.useSSL = useSSL;
        this.useKCP = useKCP;
    }
}
