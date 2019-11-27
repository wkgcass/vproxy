package vproxy.app;

public class Config {
    // a volatile long field is atomic, and we only read/assign this value, not increase
    public static volatile long currentTimestamp = System.currentTimeMillis();
    // initially we use the java impl because the FDProvider is not initiated yet

    // the default udpTimeout is the same as LVS
    // set it smaller if your environment have a smaller udp ttl
    public static final int udpTimeout = 300 * 1000;

    // the default tcpTimeout is the same as LVS
    // set it smaller if your environment have a smaller tcp session ttl
    public static final int tcpTimeout = 15 * 60_000;

    // the maximum expected size of a udp packet
    public static final int udpMtu = 65536;

    // the recommended min payload length
    // also, see Processor.PROXY_ZERO_COPY_THRESHOLD
    public static final int recommendedMinPayloadLength = 1400;
    // usually mtu is set to 1500, but some routers might set the value to 1480, 1440 or lower
    // we use 1400 here

    // smart-group-delegate or smart-node-delegate will be enabled
    public static boolean discoveryConfigProvided = false;

    // whether the loading of configuration is disabled
    // true = disabled, false = enabled
    public static boolean configLoadingDisabled = false;

    // whether the saving of configuration is disabled
    // true = disabled, false = enabled
    public static boolean configSavingDisabled = false;

    // whether modifying configuration is disabled
    // true = disabled, false = enabled
    public static boolean configModifyDisabled = false;

    // whether the program is going to stop
    // true = will stop, false = normal state
    public static boolean willStop = false;

    // whether to check before actual binding a port
    // true = will check, false = will not check
    public static boolean checkBind = true;

    // the location of auto saved file path
    // null for the default path
    public static String autoSaveFilePath = null;

    // -Dvfd=...
    // see FDProvider
    public static final String vfdImpl;

    public static final String fstack;
    public static final boolean useFStack;
    public static final String vfdlibname;

    // -Deploy=xxx
    public static final String appClass;

    static {
        appClass = System.getProperty("eploy"); // -Deploy
        fstack = System.getProperty("fstack", "");
        useFStack = !fstack.isBlank();
        vfdImpl = useFStack ? "posix" : System.getProperty("vfd", "provided");
        String vfdlibnameConf = System.getProperty("vfdlibname", "");
        if (vfdlibnameConf.isBlank()) {
            if (vfdImpl.equals("posix")) {
                if (useFStack) {
                    vfdlibname = "vfdfstack";
                } else {
                    vfdlibname = "vfdposix";
                }
            } else {
                vfdlibname = null;
            }
        } else {
            vfdlibname = vfdlibnameConf;
        }
    }
}
