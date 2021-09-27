package io.vproxy.base.dns.dnsserverlistgetter;

import io.vproxy.base.Config;
import io.vproxy.base.dns.DnsServerListGetter;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GetDnsServerListFromConfigFile implements DnsServerListGetter {
    private long fileNameServerUpdateTimestamp = 0; // set this field to -1 to indicate that the file not exists
    private List<IPPort> lastRetrievedResult;

    @Override
    public void get(boolean firstRun, Callback<List<IPPort>, Throwable> cb) {
        File f = getNameServerFile();
        if (f == null) {
            if (fileNameServerUpdateTimestamp == -1) {
                firstRun = false;
            } else {
                fileNameServerUpdateTimestamp = -1;
                firstRun = true;
            }
            if (firstRun) {
                Logger.error(LogType.ALERT, "cannot find dns server list config file");
            }
            lastRetrievedResult = Collections.emptyList();
        } else {
            long lastUpdate = f.lastModified();
            if (fileNameServerUpdateTimestamp == lastUpdate) {
                if (lastRetrievedResult != null) {
                    cb.succeeded(lastRetrievedResult);
                    return;
                }
                firstRun = false;
            } else {
                firstRun = true;
            }
            lastRetrievedResult = getNameServersFromFile(f, firstRun);
            fileNameServerUpdateTimestamp = lastUpdate;
        }
        cb.succeeded(lastRetrievedResult);
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

    private static List<IPPort> getNameServersFromFile(File f, boolean firstRun) {
        FileInputStream stream;
        try {
            stream = new FileInputStream(f);
        } catch (FileNotFoundException e) {
            Logger.shouldNotHappen("still getting FileNotFoundException while the file existence is already checked: " + f, e);
            return Collections.emptyList();
        }
        if (firstRun) {
            Logger.alert("trying to get name servers from " + f.getAbsolutePath());
        }
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            List<IPPort> ret = new ArrayList<>();
            while (true) {
                String line;
                try {
                    if ((line = br.readLine()) == null) break;
                } catch (IOException e) {
                    Logger.shouldNotHappen("reading name server conf " + f + " got exception", e);
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
                        if (ipName.startsWith("127.") || // v4 loopback address
                            ipName.equals("[::1]") || // only one ipv6 loopback address
                            ipName.startsWith("[::ffff:7f") || // v4-mapped v6
                            ipName.startsWith("[::7f")) { // v4-compatible v6
                            // special cases to exclude
                            if (!ipName.equals("127.0.0.53") // commonly used by ubuntu dns masq
                            ) {
                                continue;
                            }
                        }
                    }
                    ret.add(addr);
                } else {
                    if (firstRun) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, f + " contains invalid nameserver config: " + line);
                    }
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
