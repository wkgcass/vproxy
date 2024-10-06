package io.vproxy.xdp;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.util.IOUtils;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;
import io.vproxy.vpxdp.BPFMapReuse;
import io.vproxy.vpxdp.XDP;
import io.vproxy.vpxdp.XDPConsts;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.vproxy.xdp.Prebuilt.DEFAULT_PROG;

public class BPFObject {
    public final io.vproxy.vpxdp.BPFObject bpfobj;
    public final String nic;
    private final Map<String, BPFMap> maps = new ConcurrentHashMap<>();

    private BPFObject(io.vproxy.vpxdp.BPFObject bpfobj, String nic) {
        this.bpfobj = bpfobj;
        this.nic = nic;
    }

    public static BPFObject loadAndAttachToNic(String nicName,
                                               BPFMode mode, boolean forceAttach) throws IOException {
        return loadAndAttachToNic(DEFAULT_PROG, nicName, null, mode, forceAttach);
    }

    public static BPFObject loadAndAttachToNic(String nicName,
                                               Map<String, BPFMap> reusedMaps,
                                               BPFMode mode, boolean forceAttach) throws IOException {
        return loadAndAttachToNic(DEFAULT_PROG, nicName, reusedMaps, mode, forceAttach);
    }

    public static BPFObject loadAndAttachToNic(ByteArray bpfObjectBytes, String nicName,
                                               Map<String, BPFMap> reusedMaps,
                                               BPFMode mode, boolean forceAttach) throws IOException {
        NativeXDP.load();

        var genfilename = generateBpfObjectFile(bpfObjectBytes);

        io.vproxy.vpxdp.BPFObject bpfobj;
        try (var allocator = Allocator.ofConfined()) {
            int attachFlags = mode.mode;
            if (!forceAttach) {
                attachFlags |= XDPConsts.XDP_FLAGS_UPDATE_IF_NOEXIST;
            }
            BPFMapReuse.Array reuseArray = null;
            if (reusedMaps != null && !reusedMaps.isEmpty()) {
                reuseArray = new BPFMapReuse.Array(allocator, reusedMaps.size() + 1);
                int idx = 0;
                for (var kv : reusedMaps.entrySet()) {
                    var k = kv.getKey();
                    var v = kv.getValue();
                    reuseArray.get(idx).setName(k, allocator);
                    reuseArray.get(idx).setType(XDPConsts.VP_BPF_MAP_REUSE_TYPE_MAP);
                    reuseArray.get(idx).getUnion().setMap(v.map);
                    ++idx;
                }
                reuseArray.get(idx).setName(null);
            }
            bpfobj = XDP.get().attachBPFObjectToIfReuseMap(
                new PNIString(allocator, genfilename),
                new PNIString(allocator, Prebuilt.DEFAULT_XDP_PROG_NAME),
                new PNIString(allocator, nicName),
                attachFlags, reuseArray
            );
        }
        if (bpfobj == null) {
            throw new IOException("attach bpfobj to nic " + nicName + " failed");
        }
        return new BPFObject(bpfobj, nicName);
    }

    public static BPFObject load(ByteArray bpfObjectBytes) throws IOException {
        BPFObject obj;
        var filename = generateBpfObjectFile(bpfObjectBytes);
        try (var allocator = Allocator.ofConfined()) {
            var o = XDP.get().loadBPFObject(new PNIString(allocator, filename), null);
            if (o == null) {
                throw new IOException("loading bpfobj failed");
            }
            obj = new BPFObject(o, null);
        }
        return obj;
    }

    private static String generateBpfObjectFile(ByteArray bpfObjectBytes) throws IOException {
        byte[] bytes = bpfObjectBytes.toJavaArray();
        return IOUtils.writeTemporaryFile("kern", "o", bytes);
    }

    public BPFMap getMap(String name) throws IOException {
        if (maps.containsKey(name)) {
            return maps.get(name);
        }
        synchronized (maps) {
            if (maps.containsKey(name)) {
                return maps.get(name);
            }
            io.vproxy.vpxdp.BPFMap map;
            try (var allocator = Allocator.ofConfined()) {
                map = bpfobj.findMapByName(new PNIString(allocator, name));
            }
            if (map == null) {
                throw new IOException("failed to retrieve map " + name + " from bpfobj");
            }
            var m = new BPFMap(name, map, this);
            maps.put(name, m);
            return m;
        }
    }

    private final AtomicBoolean isReleased = new AtomicBoolean(false);

    public void release(boolean detach) {
        if (!isReleased.compareAndSet(false, true)) {
            return;
        }

        bpfobj.release();
        if (detach) {
            int res;
            try (var allocator = Allocator.ofConfined()) {
                res = XDP.get().detachBPFObjectFromIf(new PNIString(allocator, nic));
            }
            if (res != 0) {
                Logger.error(LogType.SYS_ERROR, "detaching bpf object from nic " + nic + " failed");
            }
        }
    }
}
