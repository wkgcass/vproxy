package net.cassite.vproxy.app;

public class Config {
    // the default udpTimeout is the same as LVS
    // set it smaller if your environment have a smaller udp ttl
    public static int udpTimeout = 300 * 1000;

    // service mesh mode:
    // all resources become readonly
    // and resources will be handled by auto-lb or sidecar
    public static boolean serviceMeshMode = false;
}
