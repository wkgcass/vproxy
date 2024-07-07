package io.vproxy.vfd.posix;

import io.vproxy.pni.annotation.AlwaysAligned;
import io.vproxy.pni.annotation.Name;
import io.vproxy.pni.annotation.Struct;
import io.vproxy.pni.annotation.Unsigned;

@Struct
@AlwaysAligned
@Name("SocketAddressIPv4_st")
public class PNISocketAddressIPv4ST {
    @Unsigned int ip;
    @Unsigned short port;
}
