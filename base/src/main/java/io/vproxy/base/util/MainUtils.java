package io.vproxy.base.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainUtils {
    private MainUtils() {
    }

    public static String[] checkFlagDeployInArguments(String[] args) {
        List<String> returnArgs = new ArrayList<>(args.length);
        for (final var arg : args) {
            if (!arg.startsWith("-D")) {
                returnArgs.add(arg);
                continue;
            }
            String kv = arg.substring("-D".length());
            if (!kv.contains("=")) {
                // not valid -Dkey=value format
                returnArgs.add(arg);
                continue;
            }
            String key = kv.substring(0, kv.indexOf("="));
            String value = kv.substring(kv.indexOf("=") + 1);

            if (Utils.getSystemProperty(key) != null) {
                throw new IllegalArgumentException("Cannot set -D" + key + " both in system properties " +
                    "and in program arguments");
            }

            System.setProperty(key, value);
        }
        // set dhcpGetDnsListNics if not specified in some conditions
        String deploy = Utils.getSystemProperty("deploy");
        String dhcpGetDnsListNics = Utils.getSystemProperty("dhcp_get_dns_list_nics");
        if (dhcpGetDnsListNics == null) {
            if (deploy != null) {
                if (OS.isWindows() && Arrays.asList("WebSocksProxyAgent", "WebSocksAgent", "wsagent").contains(deploy)) {
                    dhcpGetDnsListNics = "all";
                }
            }
            if (dhcpGetDnsListNics != null) {
                System.setProperty("vproxy.DhcpGetDnsListNics", dhcpGetDnsListNics);
            }
        }
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return returnArgs.toArray(new String[returnArgs.size()]);
    }
}
