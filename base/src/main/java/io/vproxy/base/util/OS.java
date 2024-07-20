package io.vproxy.base.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class OS {
    private static final String osname;
    private static final String osversion;
    private static final boolean osWin;
    private static final boolean osMac;
    private static final boolean osLinux;
    private static final String arch;
    private static final int linuxMajorVersion;
    private static final int linuxMinorVersion;

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
        } else if (arch0.equals("arm64")) {
            arch0 = "aarch64";
        }
        arch = arch0;

        int major = -1;
        int minor = -1;
        if (osLinux) {
            String[] split = osversion.split("\\.");
            if (split.length >= 2) {
                try {
                    major = Integer.parseInt(split[0]);
                    minor = Integer.parseInt(split[1]);
                } catch (NumberFormatException ignore) {
                }
            }
            if (minor == -1) {
                major = -1;
            }
        }
        linuxMajorVersion = major;
        linuxMinorVersion = minor;
    }

    private OS() {
    }

    public static String name() {
        return osname;
    }

    public static String version() {
        return osversion;
    }

    public static int major() {
        return linuxMajorVersion;
    }

    public static int minor() {
        return linuxMinorVersion;
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

    private static Charset SHELL_CHARSET = null;

    public static Charset shellCharset() {
        if (SHELL_CHARSET != null) {
            return SHELL_CHARSET;
        }
        var charsets = new ArrayList<Charset>();
        charsets.add(StandardCharsets.UTF_8);
        try {
            charsets.add(Charset.forName("GBK"));
        } catch (Throwable ignore) {
        }
        //noinspection UnnecessaryUnicodeEscape
        var testStr = "\u4F60\u597D\uFF0C\u4E16\u754C"; // 你好，世界
        String cmd;
        if (isWindows()) {
            cmd = "echo " + testStr;
        } else {
            cmd = "echo \"" + testStr + "\"";
        }
        for (var c : charsets) {
            var s = new String(cmd.getBytes(c), c);
            ProcessBuilder pb;
            if (isWindows()) {
                pb = new ProcessBuilder("cmd.exe", "/c", s);
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", s);
            }
            int exitCode;
            String stdout;
            String stderr;
            try {
                var p = pb.start();
                var ok = p.waitFor(500, TimeUnit.MILLISECONDS);
                if (!ok) {
                    Logger.warn(LogType.SYS_ERROR, "failed executing cmd: " + cmd + ", command didn't finish in 500ms");
                    try {
                        p.destroyForcibly();
                    } catch (Throwable ignore) {
                    }
                    continue;
                }
                exitCode = p.exitValue();
                stdout = new String(p.getInputStream().readAllBytes(), c);
                stderr = new String(p.getErrorStream().readAllBytes(), c);
            } catch (Exception e) {
                Logger.warn(LogType.SYS_ERROR, "failed executing cmd: " + cmd, e);
                continue;
            }
            if (exitCode != 0) {
                Logger.warn(LogType.SYS_ERROR, "failed executing cmd: " + cmd + ", exitCode = " + exitCode +
                    ", stdout = " + stdout + ", stderr = " + stderr);
                continue;
            }
            stdout = stdout.trim();
            if (testStr.equals(stdout)) {
                Logger.alert("shell charset is determined: " + c + ", getting result: " + stdout);
                SHELL_CHARSET = c;
                return c;
            } else {
                Logger.warn(LogType.ALERT, "shell charset is not " + c + ", getting result: `" + stdout + "`");
            }
        }
        Logger.warn(LogType.ALERT, "shell charset cannot be determined, using UTF-8 by default");
        SHELL_CHARSET = StandardCharsets.UTF_8;
        return SHELL_CHARSET;
    }
}
