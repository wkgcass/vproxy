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
    public static final boolean useDatagramChannel;

    // -D+A:AppClass
    public static final String appClass;

    // -D+A:Graal=true|false
    public static final boolean isGraal;

    // -D+A:EnableJs=true|false
    public static final boolean enableJs;

    static {
        String useDatagramChannelStr = System.getProperty("+A:UseDatagramChannel", "true");
        if (!useDatagramChannelStr.equals("true") && !useDatagramChannelStr.equals("false")) {
            throw new IllegalArgumentException("invalid +A:UseDatagramChannel option");
        }
        useDatagramChannel = useDatagramChannelStr.equals("true");

        appClass = System.getProperty("+A:AppClass");

        String isGraalStr = System.getProperty("+A:Graal", "false");
        if (!isGraalStr.equals("true") && !isGraalStr.equals("false")) {
            throw new IllegalArgumentException("invalid +A:Graal option");
        }
        isGraal = isGraalStr.equals("true");

        String enableJsStr = System.getProperty("+A:EnableJs", "true");
        if (!enableJsStr.equals("true") && !enableJsStr.equals("false")) {
            throw new IllegalArgumentException("invalid +A:EnableJs option");
        }
        enableJs = enableJsStr.equals("true");
    }
}
