package vproxy.base;

import vproxy.base.util.Logger;
import vproxy.base.util.OS;
import vproxy.base.util.Utils;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
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
    public static final int recommendedMinPayloadLength = 1200;
    // usually mtu is set to 1500, but some routers might set the value to 1480, 1440 or lower
    // we use 1400 here

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

    // the working directory of vproxy
    // null for the default dir
    private static String workingDirectory = null;

    public static String getWorkingDirectory() {
        String workingDirectory = Config.workingDirectory;
        if (workingDirectory == null) {
            workingDirectory = Utils.homefile(".vproxy");
        } else {
            return workingDirectory;
        }
        synchronized (Config.class) {
            if (Config.workingDirectory != null) {
                return Config.workingDirectory;
            }
            File dir = new File(workingDirectory);
            if (dir.exists()) {
                if (!dir.isDirectory()) {
                    throw new RuntimeException(dir + " exists but is not a directory");
                }
            } else {
                if (!dir.mkdirs()) {
                    throw new RuntimeException("creating vproxy dir " + dir + " failed");
                }
            }
            Config.workingDirectory = workingDirectory;
        }
        return workingDirectory;
    }

    public static String workingDirectoryFile(String name) {
        return getWorkingDirectory() + File.separator + name;
    }

    // -Deploy=xxx
    public static final String appClass;

    // -Dprobe=...
    public static final Set<String> probe;

    private static int supportReusePortLB = 0;
    // do not initialize the field statically
    // graalvm native image might initialize the field and won't be changed at runtime

    // this field is used as the domain to be resolved for health checking for dns
    // -DomainWhichShouldResolve
    public static final String domainWhichShouldResolve;

    // the config file path for mirror
    // -DmirrorConf=...
    public static final String mirrorConfigPath;

    // the nics for dhcp to use
    // -DhcpGetDnsListNics=all or eth0,eth1,... (split with ',')
    public static final boolean dhcpGetDnsListEnabled;
    public static final Predicate<String> dhcpGetDnsListNics;

    static {
        appClass = Utils.getSystemProperty("deploy");
        String probeConf = Utils.getSystemProperty("probe", "");
        if (probeConf.equals("all")) {
            probe = Set.of("virtual-fd-event", "streamed-arq-udp-event", "streamed-arq-udp-record");
        } else {
            probe = Arrays.stream(probeConf.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        }
        domainWhichShouldResolve = Utils.getSystemProperty("domain_which_should_resolve", "127.0.0.1.special.vproxy.cc");
        mirrorConfigPath = Utils.getSystemProperty("mirror_conf", "");

        String dhcpGetDnsListNicsString = Utils.getSystemProperty("dhcp_get_dns_list_nics", "");
        if (dhcpGetDnsListNicsString.isBlank()) {
            dhcpGetDnsListEnabled = false;
            dhcpGetDnsListNics = n -> false;
        } else if (dhcpGetDnsListNicsString.trim().equals("all")) {
            dhcpGetDnsListEnabled = true;
            dhcpGetDnsListNics = n -> true;
        } else {
            var set = Arrays.stream(dhcpGetDnsListNicsString.split(",")).map(String::trim).filter(n -> !n.isEmpty()).collect(Collectors.toSet());
            dhcpGetDnsListEnabled = true;
            dhcpGetDnsListNics = set::contains;
        }
    }

    public static boolean supportReusePortLB() {
        if (supportReusePortLB == -1) {
            return false;
        }
        if (supportReusePortLB == 1) {
            return true;
        }
        String os = OS.name();
        String version = OS.version();
        if (OS.isLinux()) {
            if (version.contains(".")) {
                String majorStr = version.substring(0, version.indexOf("."));
                String reset = version.substring(version.indexOf(".") + 1);
                if (reset.contains(".")) {
                    String minorStr = reset.substring(0, reset.indexOf("."));
                    try {
                        int major = Integer.parseInt(majorStr);
                        int minor = Integer.parseInt(minorStr);
                        if (major > 3 || (major == 3 && minor >= 9)) { // version >= 3.9
                            Logger.alert("reuseport load balancing across sockets supported on " + os + " " + major + "." + minor);
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
