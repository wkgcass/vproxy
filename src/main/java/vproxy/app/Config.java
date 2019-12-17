package vproxy.app;

import vproxy.util.Logger;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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

    // -Deploy=xxx
    public static final String appClass;

    // -Dprobe=...
    public static final Set<String> probe;

    private static int supportReusePortLB = 0;
    // do not initialize the field statically
    // graalvm native image might initialize the field and won't be changed at runtime

    static {
        appClass = System.getProperty("eploy"); // -Deploy
        String probeConf = System.getProperty("probe", "");
        if (probeConf.equals("all")) {
            probe = Set.of("virtual-fd-event", "streamed-arq-udp-event", "streamed-arq-udp-record");
        } else {
            probe = Arrays.stream(probeConf.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        }
    }

    public static boolean supportReusePortLB() {
        if (supportReusePortLB == -1) {
            return false;
        }
        if (supportReusePortLB == 1) {
            return true;
        }
        String os = System.getProperty("os.name", "");
        String version = System.getProperty("os.version", "");
        if (os.toLowerCase().contains("linux")) {
            if (version.contains(".")) {
                String majorStr = version.substring(0, version.indexOf("."));
                String reset = version.substring(version.indexOf(".") + 1);
                if (reset.contains(".")) {
                    String minorStr = reset.substring(0, reset.indexOf("."));
                    try {
                        int major = Integer.parseInt(majorStr);
                        int minor = Integer.parseInt(minorStr);
                        if (major > 3 || (major == 3 && minor >= 9)) { // version >= 3.9
                            assert Logger.lowLevelDebug("reuseport load balancing IS supported: " + os + " " + version);
                            supportReusePortLB = 1;
                            return true;
                        }
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }
        assert Logger.lowLevelDebug("reuseport load balancing NOT supported: " + os + " " + version);
        supportReusePortLB = -1;
        return false;
    }
}
