package io.vproxy.fubuki;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PNIString;
import io.vproxy.vfd.IPMask;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv4;
import vjson.util.ObjectBuilder;

import java.lang.foreign.MemorySegment;

public class Fubuki implements AutoCloseable {
    private static final long FUBUKI_FLAG_NO_AUTO_SPAWN = 0x0001;

    private final Data data;
    private final PNIRef<Fubuki> ref;
    private volatile FubukiHandle handle;
    private volatile boolean isClosed = false;

    public Fubuki(Data data) {
        loadNative();
        this.data = data;

        ref = PNIRef.of(this);
        startFubuki(true);
    }

    public record Data(
        int ifIndex,
        String nodeName,
        IPPort server,
        String key,
        IPMask localAddr,
        FubukiCallback callback
    ) {
    }

    private void startFubuki(boolean isFirstStart) {
        try (var allocator = Allocator.ofConfined()) {
            var opts = new FubukiStartOptions(allocator);
            opts.setCtx(ref.MEMORY);
            opts.setDeviceIndex(data.ifIndex);
            opts.setFnOnPacket(FubukiUpcall.onPacket);
            opts.setFnAddAddr(FubukiUpcall.addAddress);
            opts.setFnDeleteAddr(FubukiUpcall.deleteAddress);
            //noinspection DataFlowIssue
            var configJson = new ObjectBuilder()
                .putArray("groups", arr -> arr.addObject(o -> o
                    .put("node_name", data.nodeName)
                    .put("server_addr", data.server.formatToIPPortString())
                    .put("key", data.key)
                    .putNullableInst("tun_addr", data.localAddr == null, () ->
                        new ObjectBuilder()
                            .put("ip", data.localAddr.ip().formatToIPString())
                            .put("netmask", data.localAddr.mask().formatToIPString())
                            .build())
                ))
                .putObject("features", o -> o
                    .put("disable_api_server", true)
                    .put("disable_hosts_operation", true)
                    .put("disable_signal_handling", true)
                    .put("disable_route_operation", true)
                )
                .build().stringify();
            Logger.trace(LogType.ALERT, "fubuki node config json generated: " + configJson);
            opts.setNodeConfigJson(configJson, allocator);
            opts.setFlags(FUBUKI_FLAG_NO_AUTO_SPAWN);

            var errMsg = new PNIString(allocator.allocate(1024));
            var handle = FubukiFunc.get().start(opts, 3, errMsg);
            if (handle == null) {
                var err = errMsg.toString();
                Logger.error(LogType.SYS_ERROR, "failed to start fubuki: " + err);
                throw new RuntimeException(err);
            }
            this.handle = handle;
            if (isClosed) {
                handle.stop();
                return;
            }

            VProxyThread.create(() -> {
                try (var errStrAllocator = Allocator.ofConfined()) {
                    var errStr = new PNIString(errStrAllocator.allocate(1024));
                    int ret = handle.fubukiBlockOn(errStr);
                    Logger.warn(LogType.ALERT, "fubuki-" + data.ifIndex + " thread exits");
                    this.handle = null;
                    if (isClosed) {
                        return;
                    }
                    handle.stop();
                    data.callback.terminate(this);
                    if (ret != 0) {
                        var err = errStr.toString();
                        Logger.fatal(LogType.SYS_ERROR, "fubuki-" + data.ifIndex + " thread exits unexpectedly: code=" + ret + ", err=" + err);
                    }
                    while (!isClosed) {
                        try {
                            startFubuki(false);
                            break; // break when succeeded
                        } catch (Throwable t) {
                            try {
                                //noinspection BusyWait
                                Thread.sleep(2_000);
                            } catch (InterruptedException ignore) {
                            }
                        }
                    }
                }
            }, "fubuki-" + data.ifIndex).start();
        } catch (Throwable t) {
            Logger.fatal(LogType.SYS_ERROR, "fubuki-" + data.ifIndex + " failed to start", t);
            if (isFirstStart) {
                ref.close();
            }
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
        FubukiUpcall.setImpl(new FubukiUpcallImpl());
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
        Utils.loadDynamicLibrary("vpfubuki");
    }

    public boolean isRunning() {
        return handle != null;
    }

    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public void close() {
        if (isClosed) {
            return;
        }
        synchronized (this) {
            if (isClosed) {
                return;
            }
            isClosed = true;
        }
        ref.close();
        if (handle != null) {
            handle.stop();
        }
    }

    public void onPacket(ByteArray packet) {
        data.callback.onPacket(this, packet);
    }

    public void addAddress(IPv4 ip, IPv4 mask) {
        if (!mask.isMask()) {
            Logger.warn(LogType.SYS_ERROR, "received addAddress event: ip=" + ip + " mask=" + mask + ", not a valid mask");
            return;
        }
        data.callback.addAddress(this, ip, mask);
    }

    public void deleteAddress(IPv4 ip, IPv4 mask) {
        if (!mask.isMask()) {
            Logger.warn(LogType.SYS_ERROR, "received deleteAddress event: ip=" + ip + " mask=" + mask + ", not a valid mask");
            return;
        }
        data.callback.deleteAddress(this, ip, mask);
    }
}
