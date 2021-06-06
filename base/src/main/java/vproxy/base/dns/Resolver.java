package vproxy.base.dns;

import vproxy.base.Config;
import vproxy.base.util.*;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;
import vproxy.vfd.IPv4;
import vproxy.vfd.IPv6;

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
        AbstractResolver.getNameServers(cb);
    }

    @Blocking
    static List<IPPort> blockGetNameServers() {
        BlockCallback<List<IPPort>, RuntimeException> cb = new BlockCallback<>();
        getNameServers(cb::succeeded);
        return cb.block();
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

        long lastModified = f.lastModified();
        if (lastModified == AbstractResolver.fileHostUpdateTimestamp) {
            if (AbstractResolver.lastRetrievedHosts != null) {
                return AbstractResolver.lastRetrievedHosts;
            }
        }

        FileInputStream stream;
        try {
            stream = new FileInputStream(f);
        } catch (FileNotFoundException e) {
            Logger.shouldNotHappen("still getting FileNotFoundException while the file existence is already checked: " + f, e);
            return Collections.emptyMap();
        }
        boolean needLog = lastModified != AbstractResolver.fileHostUpdateTimestamp;

        if (needLog) {
            Logger.alert("trying to get hosts from " + f.getAbsolutePath());
        }
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            Map<String, IP> ret = new HashMap<>();
            while (true) {
                String line;
                try {
                    if ((line = br.readLine()) == null) break;
                } catch (IOException e) {
                    Logger.shouldNotHappen("reading hosts conf " + f + " got exception", e);
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
                    if (needLog) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, f + " contains invalid host config: " + line);
                    }
                    continue;
                }
                String ip = split.get(0);
                byte[] ipBytes = IP.parseIpString(ip);
                if (ipBytes == null) {
                    if (needLog) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, f + " contains invalid host config: not ip: " + line);
                    }
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
            AbstractResolver.lastRetrievedHosts = ret;
            AbstractResolver.fileHostUpdateTimestamp = lastModified;
            return ret;
        } finally {
            try {
                stream.close();
            } catch (IOException ignore) {
            }
        }
    }
}
