package io.vproxy.xdp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedBPFMapHolder {
    private static final SharedBPFMapHolder INST = new SharedBPFMapHolder();

    public static SharedBPFMapHolder getInstance() {
        return INST;
    }

    private SharedBPFMapHolder() {
    }

    private record Storage(BPFObjectAndMapProviderResultTuple tup, AtomicInteger refCount) {
    }

    public record BPFObjectAndMapProviderResultTuple(BPFObject object, BPFMap mac2portMap, BPFMap devMap) {
    }

    private final Map<String, Storage> bpfMaps = new HashMap<>();

    public interface BPFObjAndMapProvider {
        BPFObjectAndMapProviderResultTuple get() throws IOException;
    }

    public synchronized BPFObjectAndMapProviderResultTuple
    getOrCreate(String mapGroup, BPFObjAndMapProvider f) throws IOException {
        if (bpfMaps.containsKey(mapGroup)) {
            var storage = bpfMaps.get(mapGroup);
            storage.refCount.incrementAndGet();
            return storage.tup;
        }
        var tup = f.get();
        var storage = new Storage(tup, new AtomicInteger(1));
        bpfMaps.put(mapGroup, storage);
        return tup;
    }

    public synchronized void release(String mapGroup) {
        var s = bpfMaps.get(mapGroup);
        if (s == null) {
            return;
        }
        if (s.refCount.decrementAndGet() == 0) {
            bpfMaps.remove(mapGroup);
            s.tup.object.release(false);
        }
    }
}
