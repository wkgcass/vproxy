package io.vproxy.fubuki;

import io.vproxy.pni.annotation.*;

import java.lang.foreign.MemorySegment;

@Downcall
@Include("fubuki.h")
interface PNIFubukiFunc {
    @Name("fubuki_start")
    @Style(Styles.critical)
    @NoAlloc
    PNIFubukiHandle start(PNIFubukiStartOptions opts, @Unsigned int version, String errorMsg);
}

@Struct(skip = true, typedef = false)
@Include("fubuki.h")
@Name("FubukiHandle")
@PointerOnly
abstract class PNIFubukiHandle {
    @Name("if_to_fubuki")
    @Style(Styles.critical)
    @LinkerOption.Critical
    abstract void send(@Raw byte[] data, @Unsigned long len);

    @Name("fubuki_stop")
    @Style(Styles.critical)
    abstract void stop();
}

@Struct(skip = true, typedef = false)
@Include("fubuki.h")
@Name("FubukiStartOptions")
abstract class PNIFubukiStartOptions {
    MemorySegment ctx;
    String nodeConfigJson;
    @Unsigned int deviceIndex;
    MemorySegment fnOnPacket; // void (*fubuki_to_if_fn)(const uint8_t *packet, size_t len, void *ctx)
    MemorySegment fnAddAddr; // void (*add_addr_fn)(uint32_t addr, uint32_t netmask, void *ctx)
    MemorySegment fnDeleteAddr; // void (*delete_addr_fn)(uint32_t addr, uint32_t netmask, void *ctx)
}
