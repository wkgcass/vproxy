package vproxy.base.util;

public class OS {
    private static final String osname;
    private static final String osversion;
    private static final boolean osWin;
    private static final boolean osMac;
    private static final boolean osLinux;

    static {
        osname = System.getProperty("os.name", "");
        osversion = System.getProperty("os.version", "");
        String os = osname.toLowerCase();
        osLinux = os.contains("linux");
        osMac = os.contains("mac");
        osWin = os.contains("windows");
    }

    private OS() {
    }

    public static String name() {
        return osname;
    }

    public static String version() {
        return osversion;
    }

    public static boolean isWindows() {
        return osWin;
    }

    public static boolean isMac() {
        return osMac;
    }

    public static boolean isLinux() {
        return osLinux;
    }
}
