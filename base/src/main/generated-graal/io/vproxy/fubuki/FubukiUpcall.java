package io.vproxy.fubuki;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import io.vproxy.pni.graal.*;
import org.graalvm.nativeimage.*;
import org.graalvm.nativeimage.c.function.*;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;

public class FubukiUpcall {
    private static final Arena ARENA = Arena.ofShared();

    public static MemorySegment onPacket;
    public static final CEntryPointLiteral<CFunctionPointer> onPacketCEPL = GraalUtils.defineCFunctionByName(new PNILinkOptions(), io.vproxy.fubuki.FubukiUpcall.class, "onPacket");

    @CEntryPoint
    public static void onPacket(IsolateThread THREAD, VoidPointer packetPTR, long len, VoidPointer ctxPTR) {
        if (IMPL == null) {
            System.out.println("io.vproxy.fubuki.FubukiUpcall#onPacket");
            System.exit(1);
        }
        var packet = MemorySegment.ofAddress(packetPTR.rawValue());
        var ctx = MemorySegment.ofAddress(ctxPTR.rawValue());
        IMPL.onPacket(
            (packet.address() == 0 ? null : packet),
            len,
            (ctx.address() == 0 ? null : ctx)
        );
    }

    public static MemorySegment addAddress;
    public static final CEntryPointLiteral<CFunctionPointer> addAddressCEPL = GraalUtils.defineCFunctionByName(new PNILinkOptions(), io.vproxy.fubuki.FubukiUpcall.class, "addAddress");

    @CEntryPoint
    public static void addAddress(IsolateThread THREAD, int addr, int netmask, VoidPointer ctxPTR) {
        if (IMPL == null) {
            System.out.println("io.vproxy.fubuki.FubukiUpcall#addAddress");
            System.exit(1);
        }
        var ctx = MemorySegment.ofAddress(ctxPTR.rawValue());
        IMPL.addAddress(
            addr,
            netmask,
            (ctx.address() == 0 ? null : ctx)
        );
    }

    public static MemorySegment deleteAddress;
    public static final CEntryPointLiteral<CFunctionPointer> deleteAddressCEPL = GraalUtils.defineCFunctionByName(new PNILinkOptions(), io.vproxy.fubuki.FubukiUpcall.class, "deleteAddress");

    @CEntryPoint
    public static void deleteAddress(IsolateThread THREAD, int addr, int netmask, VoidPointer ctxPTR) {
        if (IMPL == null) {
            System.out.println("io.vproxy.fubuki.FubukiUpcall#deleteAddress");
            System.exit(1);
        }
        var ctx = MemorySegment.ofAddress(ctxPTR.rawValue());
        IMPL.deleteAddress(
            addr,
            netmask,
            (ctx.address() == 0 ? null : ctx)
        );
    }

    private static void setNativeImpl() {
        onPacket = MemorySegment.ofAddress(onPacketCEPL.getFunctionPointer().rawValue());
        addAddress = MemorySegment.ofAddress(addAddressCEPL.getFunctionPointer().rawValue());
        deleteAddress = MemorySegment.ofAddress(deleteAddressCEPL.getFunctionPointer().rawValue());

        var initMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions().setCritical(true), void.class, "JavaCritical_io_vproxy_fubuki_FubukiUpcall_INIT", MemorySegment.class, MemorySegment.class, MemorySegment.class);
        try {
            initMH.invoke(onPacket, addAddress, deleteAddress);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        onPacket = PanamaUtils.lookupFunctionPointer(new PNILookupOptions(), "JavaCritical_io_vproxy_fubuki_FubukiUpcall_onPacket").orElseThrow(() -> new NullPointerException("JavaCritical_io_vproxy_fubuki_FubukiUpcall_onPacket"));
        addAddress = PanamaUtils.lookupFunctionPointer(new PNILookupOptions(), "JavaCritical_io_vproxy_fubuki_FubukiUpcall_addAddress").orElseThrow(() -> new NullPointerException("JavaCritical_io_vproxy_fubuki_FubukiUpcall_addAddress"));
        deleteAddress = PanamaUtils.lookupFunctionPointer(new PNILookupOptions(), "JavaCritical_io_vproxy_fubuki_FubukiUpcall_deleteAddress").orElseThrow(() -> new NullPointerException("JavaCritical_io_vproxy_fubuki_FubukiUpcall_deleteAddress"));
    }

    private static Interface IMPL = null;

    public static void setImpl(Interface impl) {
        java.util.Objects.requireNonNull(impl);
        IMPL = impl;
        setNativeImpl();
    }

    public interface Interface {
        void onPacket(MemorySegment packet, long len, MemorySegment ctx);

        void addAddress(int addr, int netmask, MemorySegment ctx);

        void deleteAddress(int addr, int netmask, MemorySegment ctx);
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:76985067e955eb414225c2766cdccea3d97cde29be7e6d51199d994105abd08a
