package io.vproxy.fubuki;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PNIString;
import io.vproxy.vfd.IPMask;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import vjson.util.ObjectBuilder;

import java.lang.foreign.MemorySegment;

public class Fubuki implements AutoCloseable {
    private final FubukiCallback callback;
    private final PNIRef<Fubuki> ref;
    private final FubukiHandle handle;

    public Fubuki(int ifIndex, String nodeName, IPPort server, String key, IPMask localAddr, FubukiCallback callback) {
        loadNative();
        this.callback = callback;

        ref = PNIRef.of(this);
        try (var allocator = Allocator.ofConfined()) {
            var opts = new FubukiStartOptions(allocator);
            opts.setCtx(ref.MEMORY);
            opts.setDeviceIndex(ifIndex);
            opts.setFnOnPacket(FubukiUpcall.onPacket);
            opts.setFnAddAddr(FubukiUpcall.addAddress);
            opts.setFnDeleteAddr(FubukiUpcall.deleteAddress);
            //noinspection DataFlowIssue
            var configJson = new ObjectBuilder()
                .putArray("groups", arr -> arr.addObject(o -> o
                    .put("node_name", nodeName)
                    .put("server_addr", server.formatToIPPortString())
                    .put("key", key)
                    .putNullableInst("tun_addr", localAddr == null, () ->
                        new ObjectBuilder()
                            .put("ip", localAddr.ip().formatToIPString())
                            .put("netmask", localAddr.mask().formatToIPString())
                            .build())
                ))
                .putObject("features", o -> o
                    .put("disable_api_server", true)
                    .put("disable_hosts_operation", true)
                    .put("disable_signal_handling", true)
                    .put("disable_route_operation", true)
                )
                .build().stringify();
            Logger.trace(LogType.ALERT, STR."fubuki node config json generated: \{configJson}");
            opts.setNodeConfigJson(configJson, allocator);

            var errMsg = new PNIString(allocator.allocate(1024));
            handle = FubukiFunc.get().start(opts, 1, errMsg);

            if (handle == null) {
                var err = errMsg.toString();
                Logger.error(LogType.SYS_ERROR, STR."failed to start fubuki: \{err}");
                throw new RuntimeException(err);
            }
        } catch (Throwable t) {
            ref.close();
            throw t;
        }
    }

    public void send(MemorySegment data) {
        handle.send(data, data.byteSize());
    }

    private static boolean loaded = false;

    private static void loadNative() {
        if (loaded) {
            return;
        }
        synchronized (Fubuki.class) {
            if (loaded) {
                return;
            }
            doLoad();
            loaded = true;
        }
    }

    private static void doLoad() {
        try {
            Utils.loadDynamicLibrary("fubuki");
        } catch (UnsatisfiedLinkError e) {
            try {
                Utils.loadDynamicLibrary("fubukil");
            } catch (UnsatisfiedLinkError _) {
                throw e;
            }
        }
    }

    @Override
    public void close() {
        ref.close();
        if (handle != null) {
            handle.stop();
        }
    }

    public void onPacket(ByteArray packet) {
        callback.onPacket(this, packet);
    }

    public void addAddress(IPv4 ip, IPv4 mask) {
        if (!mask.isMask()) {
            Logger.warn(LogType.SYS_ERROR, STR."received addAddress event: ip=\{ip} mask=\{mask}, not a valid mask");
            return;
        }
        callback.addAddress(this, ip, mask);
    }

    public void deleteAddress(IPv4 ip, IPv4 mask) {
        if (!mask.isMask()) {
            Logger.warn(LogType.SYS_ERROR, STR."received deleteAddress event: ip=\{ip} mask=\{mask}, not a valid mask");
            return;
        }
        callback.deleteAddress(this, ip, mask);
    }
}
