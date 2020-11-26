package vproxybase.dns;

import vfd.IP;
import vfd.IPPort;
import vfd.IPv4;
import vfd.IPv6;
import vproxybase.Config;
import vproxybase.dhcp.DHCPClientHelper;
import vproxybase.util.*;

import java.io.*;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface Resolver {
    void resolve(String host, Callback<? super IP, ? super UnknownHostException> cb);

    void resolve(String host, boolean ipv4, boolean ipv6, Callback<? super IP, ? super UnknownHostException> cb);

    void resolveV6(String host, Callback<? super IPv6, ? super UnknownHostException> cb);

    void resolveV4(String host, Callback<? super IPv4, ? super UnknownHostException> cb);

    default IP blockResolve(String host) throws UnknownHostException {
        BlockCallback<IP, UnknownHostException> cb = new BlockCallback<>();
        resolve(host, cb);
        return cb.block();
    }

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

    static void getNameServers(Consumer<List<IPPort>> cb) {
        // ensure default resolver is running (will need an event loop)
        Resolver defaultResolver = getDefault();

        // use the callback util to execute callback on the caller event loop
        Callback<List<IPPort>, Throwable> threadAwareCallback = new Callback<>() {
            @Override
            protected void onSucceeded(List<IPPort> value) {
                cb.accept(value);
            }

            @Override
            protected void onFailed(Throwable err) {
                // will not happen
            }
        };

        File f = getNameServerFile();
        boolean needLog;
        if (f == null) {
            if (AbstractResolver.fileNameServerUpdateTimestamp == -1) {
                needLog = false;
            } else {
                AbstractResolver.fileNameServerUpdateTimestamp = -1;
                needLog = true;
            }
        } else {
            long lastUpdate = f.lastModified();
            if (AbstractResolver.fileNameServerUpdateTimestamp == lastUpdate) {
                needLog = false;
            } else {
                AbstractResolver.fileNameServerUpdateTimestamp = lastUpdate;
                needLog = true;
            }
        }
        List<IPPort> ret;
        if (f == null) {
            ret = Collections.emptyList();
        } else {
            ret = getNameServersFromFile(f, needLog);
        }
        if (!ret.isEmpty()) {
            threadAwareCallback.succeeded(ret);
            return;
        }
        DHCPClientHelper.getDomainNameServers(((AbstractResolver) defaultResolver).getLoop().getSelectorEventLoop(),
            Config.dhcpNics, 1, new Callback<>() {
                @Override
                protected void onSucceeded(Set<IP> nameServerIPs) {
                    threadAwareCallback.succeeded(nameServerIPs.stream().map(ip -> new IPPort(ip, 53)).collect(Collectors.toList()));
                }

                @Override
                protected void onFailed(IOException err) {
                    if (needLog) {
                        Logger.error(LogType.ALERT, "failed retrieving dns servers from dhcp", err);
                        Logger.alert("using 8.8.8.8 and 8.8.4.4 as name servers");
                    }

                    var ret = new ArrayList<IPPort>(2);
                    ret.add(new IPPort(IP.from(new byte[]{8, 8, 8, 8}), 53));
                    ret.add(new IPPort(IP.from(new byte[]{8, 8, 4, 4}), 53));

                    threadAwareCallback.succeeded(ret);
                }
            });
    }

    private static File getNameServerFile() {
        // try ~/resolv.conf for customized resolve configuration
        File f = new File(Config.workingDirectoryFile("resolv.conf"));
        if (f.exists() && f.isFile()) {
            return f;
        }
        // try linux|bsd resolve configuration
        f = new File("/etc/resolv.conf");
        if (f.exists() && f.isFile()) {
            return f;
        }
        // still not found
        return null;
    }

    private static List<IPPort> getNameServersFromFile(File f, boolean needLog) {
        FileInputStream stream;
        try {
            stream = new FileInputStream(f);
        } catch (FileNotFoundException e) {
            Logger.shouldNotHappen("still getting FileNotFoundException while the file existence is already checked: " + f, e);
            return Collections.emptyList();
        }
        if (needLog)
            Logger.alert("trying to get name servers from " + f.getAbsolutePath());
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            List<IPPort> ret = new ArrayList<>();
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
                if (IP.isIpLiteral(line)) {
                    IPPort addr = new IPPort(IP.from(line), 53);
                    // need to remove localhost addresses because it might be vproxy itself
                    {
                        String ipName = addr.getAddress().formatToIPString();
                        if (ipName.startsWith("127.") ||
                            ipName.equals("[::1]") || // only one ipv6 loopback address
                            ipName.startsWith("[::ffff:7f]") || // v4-mapped v6
                            ipName.startsWith("[::7f]")) { // v4-compatible v6
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

    static Map<String, IP> getHosts() {
        // try ~/hosts for customized host config
        File f = new File(Config.workingDirectoryFile("hosts"));
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
        boolean needLog = f.lastModified() != AbstractResolver.fileHostUpdateTimestamp;
        AbstractResolver.fileHostUpdateTimestamp = f.lastModified();

        if (needLog)
            Logger.alert("trying to get hosts from " + f.getAbsolutePath());
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            Map<String, IP> ret = new HashMap<>();
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
                byte[] ipBytes = IP.parseIpString(ip);
                if (ipBytes == null) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, f + " contains invalid host config: not ip: " + line);
                    continue;
                }
                IP l3addr = IP.from(ipBytes);
                for (int i = 1; i < split.size(); ++i) {
                    String domain1 = split.get(i);
                    String domain2;
                    if (domain1.endsWith(".")) {
                        domain2 = domain1.substring(0, domain1.length() - 1);
                    } else {
                        domain2 = domain1 + ".";
                    }
                    if (ret.containsKey(domain1) || ret.containsKey(domain2)) { // only consider the first present domain
                        continue;
                    }
                    ret.put(domain1, l3addr);
                    ret.put(domain2, l3addr);
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
