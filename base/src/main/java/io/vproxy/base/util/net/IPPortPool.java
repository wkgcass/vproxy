package io.vproxy.base.util.net;

import io.vproxy.base.util.coll.Tuple;
import io.vproxy.dep.vjson.parser.ParserUtils;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;

import java.util.*;

public class IPPortPool {
    private final ArrayList<Tuple<IP, PortPool>> pool = new ArrayList<>();
    private final Map<IP, PortPool> poolMap = new HashMap<>();

    private int cursor = 0;

    /*
     * example:
     * ipport: 192.168.0.1:1000
     * multiple ports: 192.168.0.1:1000.1001
     * port range: 192.168.0.1:1000-2000
     * multiple ports and port ranges: 192.168.0.1:1000.1001.1005-1007.1009-1020
     * multiple ips: use \r\n or \n or \r or | or / to separate multiple ip config
     */
    public IPPortPool(String expr) {
        List<String> configs = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (char c : expr.toCharArray()) {
            if (ParserUtils.isWhiteSpace(c)) {
                continue;
            }
            if (c == '\r' || c == '\n' || c == '|' || c == '/') {
                String s = sb.toString();
                sb.delete(0, sb.length());
                if (s.isBlank()) {
                    continue;
                }
                configs.add(s.trim());
            } else {
                sb.append(c);
            }
        }
        if (!sb.toString().isBlank()) {
            configs.add(sb.toString().trim());
        }

        var ips = new HashSet<IP>();

        for (String line : configs) {
            String[] ipAndPort = line.split(":");
            if (ipAndPort.length != 2) {
                throw new IllegalArgumentException("invalid config: " + line);
            }
            String ipStr = ipAndPort[0];
            if (!IP.isIpLiteral(ipStr)) {
                throw new IllegalArgumentException("invalid config: " + ipStr + " is not a valid ip: " + ipStr);
            }
            IP ip = IP.from(ipStr);
            if (!ips.add(ip)) {
                throw new IllegalArgumentException("duplicated ip: " + ip.formatToIPString() + ": " + ipStr);
            }

            String portExp = ipAndPort[1];
            PortPool portPool;
            try {
                portPool = new PortPool(portExp);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid config when parsing port expression: " + e.getMessage() + ": " + ipStr);
            }
            pool.add(new Tuple<>(ip, portPool));
        }

        // format the poolMap
        for (var tup : pool) {
            poolMap.put(tup._1, tup._2);
        }
    }

    private void nextCursor() {
        int c = cursor;
        if (c + 1 >= pool.size()) {
            cursor = 0;
        } else {
            cursor += 1;
        }
    }

    // return null when allocation failed
    public IPPort allocate() {
        for (int i = 0; i < pool.size(); ++i) {
            var tuple = pool.get(cursor);
            int port = tuple._2.allocate();
            if (port != 0) {
                return new IPPort(tuple._1, port);
            }
            nextCursor();
        }
        return null;
    }

    public void release(IPPort ipport) {
        var pool = poolMap.get(ipport.getAddress());
        if (pool == null) {
            throw new IllegalArgumentException("not within this pool");
        }
        pool.release(ipport.getPort());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IPPortPool that = (IPPortPool) o;
        return pool.equals(that.pool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pool);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (var tup : pool) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append("/");
            }
            sb.append(tup._1.formatToIPString()).append(":").append(tup._2.toString());
        }
        return sb.toString();
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (var tup : pool) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append("/");
            }
            sb.append(tup._1.formatToIPString()).append(":").append(tup._2.serialize());
        }
        return sb.toString();
    }
}
