package io.vproxy.vproxyx.nexus;

public class NexusUtils {
    private NexusUtils() {
    }

    public static boolean isNotValidNodeName(String name) {
        var chars = name.toCharArray();
        for (var c : chars) {
            if ('a' <= c && c <= 'z')
                continue;
            if ('A' <= c && c <= 'Z')
                continue;
            if ('0' <= c && c <= '9')
                continue;
            if (c == '_' || c == '-')
                continue;
            return true;
        }
        return false;
    }
}
