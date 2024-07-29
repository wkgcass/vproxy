package io.vproxy.vproxyx.nexus.entity;

import io.vproxy.vfd.IPPort;

public record ConnectInfo(IPPort ipport, int uotPort) {
}
