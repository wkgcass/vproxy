package vfd;

public class VFDConfig {
    private VFDConfig() {
    }

    // -Dvfd=provided
    // see FDProvider
    public static final String vfdImpl;

    // -Dfstack="--conf /etc/f-stack.conf"
    public static final String fstack;
    public static final boolean useFStack;

    // -Dvfdlibname="vfdposix"
    public static String vfdlibname;

    // -Dvfdtrace=1
    public static final boolean vfdtrace;

    static {
        fstack = System.getProperty("fstack", "");
        useFStack = !fstack.isBlank();
        vfdImpl = useFStack ? "posix" : System.getProperty("vfd", "provided");
        if (!vfdImpl.equals("provided")) {
            if (useFStack) {
                vfdlibname = "vfdfstack";
            } else {
                vfdlibname = "vfdposix";
            }
        }

        String vfdtraceConf = System.getProperty("vfdtrace", "0");
        vfdtrace = !vfdtraceConf.equals("0");
    }
}
