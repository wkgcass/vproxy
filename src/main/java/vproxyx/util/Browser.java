package vproxyx.util;

import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.OS;

import java.io.IOException;

public class Browser {
    private Browser() {
    }

    public static boolean open(String uri) {
        if (uri.contains("://")) {
            if (!uri.startsWith("https://") && !uri.startsWith("http://"))
                throw new IllegalArgumentException("invalid uri for browser: " + uri);
        } else {
            uri = "http://" + uri;
        }
        String cmd;
        if (OS.isWindows()) {
            cmd = "explorer";
        } else if (OS.isMac()) {
            cmd = "open";
        } else {
            return false;
        }
        try {
            Process p = new ProcessBuilder().command(cmd, uri).start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            Logger.error(LogType.SYS_ERROR, "executing " + cmd + " " + uri + " failed", e);
            return false;
        }
    }
}
