package net.cassite.vproxy.app;

public class Config {
    // a volatile long field is atomic, and we only read/assign this value, not increase
    public static volatile long currentTimestamp = System.currentTimeMillis();

    // the default udpTimeout is the same as LVS
    // set it smaller if your environment have a smaller udp ttl
    public static final int udpTimeout = 300 * 1000;

    // the default tcpTimeout is the same as LVS
    // set it smaller if your environment have a smaller tcp session ttl
    public static final int tcpTimeout = 15 * 60_000;

    // the recommended min payload length
    // also, see Processor.PROXY_ZERO_COPY_THRESHOLD
    public static final int recommendedMinPayloadLength = (int) (1.5 * (14 + 20 + 32));

    // service mesh mode:
    // all resources become readonly
    // and resources will be handled by smart-lb-group or sidecar
    public static boolean serviceMeshConfigProvided = false;

    // whether the loading of configuration is disabled
    // true = disabled, false = enabled
    public static boolean configLoadingDisabled = false;

    // whether the saving of configuration is disabled
    // true = disabled, false = enabled
    public static boolean configSavingDisabled = false;

    // whether modifying configuration is disabled
    // true = disabled, false = enabled
    public static boolean configModifyDisabled = false;

    // -Deploy=xxx
    public static final String appClass;

    static {
        appClass = System.getProperty("eploy"); // -Deploy
    }
}
