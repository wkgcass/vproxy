package vproxy.base.util;

public class OS {
    private static final String osname;
    private static final String osversion;
    private static final boolean osWin;
    private static final boolean osMac;
    private static final boolean osLinux;
    private static final String arch;

    static {
        osname = System.getProperty("os.name", "");
        osversion = System.getProperty("os.version", "");
        String os = osname.toLowerCase();
        osLinux = os.contains("linux");
        osMac = os.contains("mac");
        osWin = os.contains("windows");
        var arch0 = System.getProperty("os.arch", "x86_64" /*most java users are x86_64*/);
        // fix java returned arch
        if (arch0.equals("amd64")) {
            arch0 = "x86_64";
        }
        arch = arch0;
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

    public static String arch() {
        return arch;
    }
}
