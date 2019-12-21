package vproxy.dns;

import vproxy.selector.SelectorEventLoop;
import vproxy.util.Callback;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

public interface Resolver {
    void resolve(String host, Callback<? super InetAddress, ? super UnknownHostException> cb);

    void resolve(String host, boolean ipv4, boolean ipv6, Callback<? super InetAddress, ? super UnknownHostException> cb);

    void resolveV6(String host, Callback<? super Inet6Address, ? super UnknownHostException> cb);

    void resolveV4(String host, Callback<? super Inet4Address, ? super UnknownHostException> cb);

    int cacheCount();

    void copyCache(Collection<? super Cache> cacheList);

    static Resolver getDefault() {
        return AbstractResolver.getDefault();
    }

    static void stopDefault() {
        AbstractResolver.stopDefault();
    }

    void addListener(ResolveListener listener);

    void clearCache();

    void start();

    void stop() throws IOException;

    static List<InetSocketAddress> getNameServers() {
        // try ~/resolv.conf for customized resolve configuration
        File f = new File(System.getProperty("user.home") + "/resolv.conf");
        if (!f.exists() || !f.isFile()) { // try linux|bsd resolve configuration
            f = new File("/etc/resolv.conf");
        }
        if (!f.exists() || !f.isFile()) { // still not found
            return Collections.emptyList();
        }
        FileInputStream stream;
        try {
            stream = new FileInputStream(f);
        } catch (FileNotFoundException e) {
            Logger.shouldNotHappen("still getting FileNotFoundException while the file existence is already checked: " + f, e);
            return Collections.emptyList();
        }
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            List<InetSocketAddress> ret = new ArrayList<>();
            while (true) {
                String line;
                try {
                    if ((line = br.readLine()) == null) break;
                } catch (IOException e) {
                    Logger.shouldNotHappen("reading " + f + " got exception", e);
                    return ret;
                }
                if (line.contains("#")) {
                    line = line.substring(0, line.indexOf("#")); // remove comment
                }
                line = line.trim();
                if (line.startsWith("nameserver ")) {
                    line = line.substring("nameserver ".length());
                } else {
                    continue;
                }
                if (Utils.isIpLiteral(line)) {
                    InetSocketAddress addr;
                    try {
                        addr = new InetSocketAddress(InetAddress.getByName(line), 53);
                    } catch (UnknownHostException e) {
                        Logger.shouldNotHappen("creating l3addr from ip literal failed: " + line);
                        continue;
                    }
                    // need to remove localhost addresses because it might be vproxy itself
                    {
                        String ipName = Utils.ipStr(addr.getAddress().getAddress());
                        if (ipName.equals("127.0.0.1") ||
                            ipName.equals("[0000:0000:0000:0000:0000:0000:0000:0001]") ||
                            ipName.equals("[0000:0000:0000:0000:0000:ffff:7f00:0001]") || // v4-mapped v6
                            ipName.equals("[0000:0000:0000:0000:0000:0000:7f00:0001]")) { // v4-compatible v6
                            continue;
                        }
                    }
                    ret.add(addr);
                } else {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, f + " contains invalid nameserver config: " + line);
                }
            }
            return ret;
        } finally {
            try {
                stream.close();
            } catch (IOException ignore) {
            }
        }
    }

    static Map<String, InetAddress> getHosts() {
        // try ~/hosts for customized host config
        File f = new File(System.getProperty("user.home") + "/hosts");
        if (!f.exists() || !f.isFile()) { // try linux|bsd host file
            f = new File("/etc/hosts");
        }
        if (!f.exists() || !f.isFile()) { // try windows host file
            f = new File("c:\\Windows\\System32\\Drivers\\etc\\hosts");
        }
        if (!f.exists() || !f.isFile()) {
            return Collections.emptyMap();
        }
        FileInputStream stream;
        try {
            stream = new FileInputStream(f);
        } catch (FileNotFoundException e) {
            Logger.shouldNotHappen("still getting FileNotFoundException while the file existence is already checked: " + f, e);
            return Collections.emptyMap();
        }
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            Map<String, InetAddress> ret = new HashMap<>();
            while (true) {
                String line;
                try {
                    if ((line = br.readLine()) == null) break;
                } catch (IOException e) {
                    Logger.shouldNotHappen("reading " + f + " got exception", e);
                    return ret;
                }
                if (line.contains("#")) {
                    line = line.substring(0, line.indexOf("#")); // remove comment
                }
                if (line.isBlank()) {
                    continue; // ignore empty line or lines with only comment in it
                }
                line = line.trim();
                List<String> split = Arrays.asList(line.split("[ \\t]"));
                split = split.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                if (split.size() < 2) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, f + " contains invalid host config: " + line);
                    continue;
                }
                String ip = split.get(0);
                byte[] ipBytes = Utils.parseIpString(ip);
                if (ipBytes == null) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, f + " contains invalid host config: not ip: " + line);
                    continue;
                }
                InetAddress l3addr;
                try {
                    l3addr = InetAddress.getByAddress(ipBytes);
                } catch (UnknownHostException e) {
                    Logger.shouldNotHappen("retrieving l3addr failed", e);
                    continue;
                }
                for (int i = 1; i < split.size(); ++i) {
                    ret.put(split.get(i), l3addr);
                }
            }
            return ret;
        } finally {
            try {
                stream.close();
            } catch (IOException ignore) {
            }
        }
    }
}
