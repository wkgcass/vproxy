package io.vproxy.fubuki;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class FubukiUpcall {
    private static final Arena ARENA = Arena.ofShared();

    public static final MemorySegment onPacket;

    private static void onPacket(MemorySegment packet, long len, MemorySegment ctx) {
        if (IMPL == null) {
            System.out.println("io.vproxy.fubuki.FubukiUpcall#onPacket");
            System.exit(1);
        }
        IMPL.onPacket(
            (packet.address() == 0 ? null : packet),
            len,
            (ctx.address() == 0 ? null : ctx)
        );
    }

    public static final MemorySegment addAddress;

    private static void addAddress(int addr, int netmask, MemorySegment ctx) {
        if (IMPL == null) {
            System.out.println("io.vproxy.fubuki.FubukiUpcall#addAddress");
            System.exit(1);
        }
        IMPL.addAddress(
            addr,
            netmask,
            (ctx.address() == 0 ? null : ctx)
        );
    }

    public static final MemorySegment deleteAddress;

    private static void deleteAddress(int addr, int netmask, MemorySegment ctx) {
        if (IMPL == null) {
            System.out.println("io.vproxy.fubuki.FubukiUpcall#deleteAddress");
            System.exit(1);
        }
        IMPL.deleteAddress(
            addr,
            netmask,
            (ctx.address() == 0 ? null : ctx)
        );
    }

    static {
        MethodHandle onPacketMH;
        MethodHandle addAddressMH;
        MethodHandle deleteAddressMH;
        try {
            onPacketMH = MethodHandles.lookup().findStatic(io.vproxy.fubuki.FubukiUpcall.class, "onPacket", MethodType.methodType(void.class, MemorySegment.class, long.class, MemorySegment.class));
            addAddressMH = MethodHandles.lookup().findStatic(io.vproxy.fubuki.FubukiUpcall.class, "addAddress", MethodType.methodType(void.class, int.class, int.class, MemorySegment.class));
            deleteAddressMH = MethodHandles.lookup().findStatic(io.vproxy.fubuki.FubukiUpcall.class, "deleteAddress", MethodType.methodType(void.class, int.class, int.class, MemorySegment.class));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        onPacket = PanamaUtils.defineCFunction(new PNILinkOptions(), ARENA, onPacketMH, void.class, MemorySegment.class, long.class, MemorySegment.class);
        addAddress = PanamaUtils.defineCFunction(new PNILinkOptions(), ARENA, addAddressMH, void.class, int.class, int.class, MemorySegment.class);
        deleteAddress = PanamaUtils.defineCFunction(new PNILinkOptions(), ARENA, deleteAddressMH, void.class, int.class, int.class, MemorySegment.class);

        var initMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions().setCritical(true), void.class, "JavaCritical_io_vproxy_fubuki_FubukiUpcall_INIT", MemorySegment.class, MemorySegment.class, MemorySegment.class);
        try {
            initMH.invoke(onPacket, addAddress, deleteAddress);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static Interface IMPL = null;

    public static void setImpl(Interface impl) {
        java.util.Objects.requireNonNull(impl);
        IMPL = impl;
    }

    public interface Interface {
        void onPacket(MemorySegment packet, long len, MemorySegment ctx);

        void addAddress(int addr, int netmask, MemorySegment ctx);

        void deleteAddress(int addr, int netmask, MemorySegment ctx);
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:82b1ccc6e3cf2038f74c29aad588fc1b024b0b61f371bf30163dcb9b70d78565
