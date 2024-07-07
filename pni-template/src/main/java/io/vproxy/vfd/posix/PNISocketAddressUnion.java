package io.vproxy.vfd.posix;

import io.vproxy.pni.annotation.Union;

@Union
public class PNISocketAddressUnion {
    PNISocketAddressIPv4ST v4;
    PNISocketAddressIPv6ST v6;
}
