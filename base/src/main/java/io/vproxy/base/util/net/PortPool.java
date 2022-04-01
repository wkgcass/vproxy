package io.vproxy.base.util.net;

import io.vproxy.base.util.Utils;
import io.vproxy.base.util.misc.IntMatcher;

import java.util.Arrays;
import java.util.Objects;

public class PortPool implements IntMatcher {
    private final int minPort;
    private final boolean[] ports;
    private final boolean[] originalPorts;

    private int cursor = 0;

    public PortPool(String expr) {
        int minPort = 0;
        int lastPort = 0;
        boolean[] ports = new boolean[65536];

        if (expr.isBlank()) {
            throw new IllegalArgumentException("empty expression");
        }
        String[] split = expr.split("\\.");
        for (String s : split) {
            if (s.contains("-")) {
                // is range

                String[] pair = s.split("-");
                if (pair.length != 2) {
                    throw new IllegalArgumentException("invalid expression: port range must be $port1-$port2: " + s);
                }
                String minS = pair[0];
                String maxS = pair[1];

                if (!Utils.isInteger(minS)) {
                    throw new IllegalArgumentException("expecting port number, but got " + minS);
                }
                int min = Integer.parseInt(minS);
                if (min <= lastPort) {
                    throw new IllegalArgumentException("port numbers must always increase: " + min);
                }
                if (min > 65534) {
                    throw new IllegalArgumentException("the min port numbers in port ranges must not exceed 65534: " + min);
                }

                if (!Utils.isInteger(maxS)) {
                    throw new IllegalArgumentException("expecting port number, but got " + maxS);
                }
                int max = Integer.parseInt(maxS);
                if (max <= min) {
                    throw new IllegalArgumentException("port numbers must always increase: " + max);
                }
                if (max > 65535) {
                    throw new IllegalArgumentException("port numbers must not exceed 65535: " + max);
                }

                lastPort = max;

                if (minPort == 0) {
                    minPort = min;
                }

                for (int i = min; i <= max; ++i) {
                    ports[i] = true;
                }
            } else {
                // is port number

                if (!Utils.isInteger(s)) {
                    throw new IllegalArgumentException("expecting port number, but got " + s);
                }
                int p = Integer.parseInt(s);
                if (p <= lastPort) {
                    throw new IllegalArgumentException("port numbers must always increase: " + p);
                }
                if (p > 65535) {
                    throw new IllegalArgumentException("port numbers must not exceed 65535: " + p);
                }
                lastPort = p;

                if (minPort == 0) {
                    minPort = p;
                }

                ports[p] = true;
            }
        }

        this.minPort = minPort;
        this.ports = new boolean[lastPort + 1 - minPort];
        this.originalPorts = new boolean[this.ports.length];
        System.arraycopy(ports, minPort, this.ports, 0, this.ports.length);
        System.arraycopy(this.ports, 0, this.originalPorts, 0, this.ports.length);
    }

    private int nextCursor() {
        int c = cursor;
        if (c + 1 >= ports.length) {
            cursor = 0;
        } else {
            cursor += 1;
        }
        return c;
    }

    // return 0 when allocation failed
    public int allocate() {
        for (int i = 0; i < this.ports.length; ++i) {
            int index = nextCursor();
            if (ports[index]) {
                ports[index] = false;
                return minPort + index;
            }
        }
        return 0;
    }

    public void release(int port) {
        if (port < minPort || port >= minPort + ports.length) {
            throw new IllegalArgumentException("not within this pool");
        }
        if (!originalPorts[port - minPort]) {
            throw new IllegalArgumentException("not within this pool");
        }
        if (ports[port - minPort]) {
            throw new IllegalArgumentException("not allocated");
        }
        ports[port - minPort] = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PortPool portPool = (PortPool) o;
        return minPort == portPool.minPort && Arrays.equals(originalPorts, portPool.originalPorts);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(minPort);
        result = 31 * result + Arrays.hashCode(originalPorts);
        return result;
    }

    @Override
    public String toString() {
        return "raw=" + formatToString(originalPorts) + ",current=" + formatToString(ports);
    }

    public String serialize() {
        return formatToString(originalPorts);
    }

    private String formatToString(boolean[] portArray) {
        StringBuilder sb = new StringBuilder();
        int beginIndex = -1;
        int i;
        boolean requireAppendAfterLoop = false;
        for (i = 0; i < portArray.length; ++i) {
            if (portArray[i]) {
                if (beginIndex == -1) {
                    beginIndex = i;
                }
                requireAppendAfterLoop = true;
            } else {
                if (beginIndex != -1) {
                    doAppend(sb, beginIndex, i);
                    beginIndex = -1;
                    requireAppendAfterLoop = false;
                }
            }
        }
        if (requireAppendAfterLoop) {
            doAppend(sb, beginIndex, i);
        }
        return sb.toString();
    }

    private void doAppend(StringBuilder sb, int beginIndex, int i) {
        if (sb.length() != 0) {
            sb.append(".");
        }
        if (beginIndex == i - 1) {
            sb.append(minPort + beginIndex);
        } else if (beginIndex == i - 2) {
            sb.append(minPort + beginIndex).append(".").append(minPort + beginIndex + 1);
        } else {
            sb.append(minPort + beginIndex).append("-").append(minPort + i - 1);
        }
    }

    @Override
    public boolean match(int n) {
        if (n < minPort) {
            return false;
        }
        if (n >= minPort + ports.length) {
            return false;
        }
        return ports[n - minPort];
    }
}
