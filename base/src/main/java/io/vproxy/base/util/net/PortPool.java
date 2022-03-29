package io.vproxy.base.util.net;

import io.vproxy.base.util.Utils;

public class PortPool {
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
}
