package io.vproxy.xdp;

import io.vproxy.base.util.coll.Tuple;

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

    private record Storage(BPFObject bpfObject, BPFMap bpfMap, AtomicInteger refCount) {
    }

    private final Map<String, Storage> bpfMaps = new HashMap<>();

    public interface BPFObjAndMapProvider {
        Tuple<BPFObject, BPFMap> get() throws IOException;
    }

    public synchronized BPFMap getOrCreate(String mapGroup, BPFObjAndMapProvider f) throws IOException {
        if (bpfMaps.containsKey(mapGroup)) {
            var storage = bpfMaps.get(mapGroup);
            storage.refCount.incrementAndGet();
            return storage.bpfMap;
        }
        var tup = f.get();
        var storage = new Storage(tup._1, tup._2, new AtomicInteger(1));
        bpfMaps.put(mapGroup, storage);
        return tup._2;
    }

    public synchronized void release(String mapGroup) {
        var s = bpfMaps.get(mapGroup);
        if (s == null) {
            return;
        }
        if (s.refCount.decrementAndGet() == 0) {
            bpfMaps.remove(mapGroup);
            s.bpfObject.release(false);
        }
    }
}
