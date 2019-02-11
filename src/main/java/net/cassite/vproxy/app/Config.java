package net.cassite.vproxy.app;

public class Config {
    // the default udpTimeout is the same as LVS
    // set it smaller if your environment have a smaller udp ttl
    public static int udpTimeout = 300 * 1000;

    // service mesh mode:
    // all resources become readonly
    // and resources will be handled by auto-lb or sidecar
    public static boolean serviceMeshMode = false;

    // currently, DatagramChannel is not supported by graalvm native image
    // so we use DatagramSocket for native-image instead
    // this flag is set to true by default when running on jvm
    // to gain some performance
    //
    // this only effects udp usage in Discovery.java
    //
    // use system property:
    // -D+A:UseDatagramChannel=true|false
    public static boolean useDatagramChannel = true;

    static {
        String useDatagramChannel = System.getProperty("+A:UseDatagramChannel", "true");
        if (!useDatagramChannel.equals("true") && !useDatagramChannel.equals("false")) {
            throw new IllegalArgumentException("invalid +A:UseDatagramChannel option");
        }
        if (useDatagramChannel.equals("false")) {
            Config.useDatagramChannel = false;
        }
    }
}
