package io.vproxy.vfd.posix;

import io.vproxy.pni.annotation.*;

@Struct
@AlwaysAligned
@Name("SocketAddressIPv6_st")
public class PNISocketAddressIPv6ST {
    @Len(40) String ip;
    @Unsigned short port;
}
