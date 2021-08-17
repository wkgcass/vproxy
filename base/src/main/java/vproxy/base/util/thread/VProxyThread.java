package vproxy.base.util.thread;

import vjson.parser.ArrayParser;
import vjson.parser.ObjectParser;
import vjson.parser.StringParser;
import vjson.util.StringDictionary;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.ByteArray;
import vproxy.base.util.Logger;
import vproxy.base.util.coll.LRUMap;
import vproxy.base.util.objectpool.PrototypeObjectList;
import vproxy.vfd.IP;
import vproxy.vfd.IPv4;
import vproxy.vfd.IPv6;
import vproxy.vfd.MacAddress;
import vproxy.xdp.Chunk;

import java.util.UUID;

public interface VProxyThread {
    ThreadLocal<VProxyThreadVariable> threadLocal = new ThreadLocal<>();

    static VProxyThread create(Runnable runnable, String name) {
        return new VProxyThreadImpl(runnable, name);
    }

    static VProxyThreadVariable current() {
        Thread t = Thread.currentThread();
        if (t instanceof VProxyThread) {
            return ((VProxyThread) t).getVariable();
        }
        VProxyThreadVariable vt = threadLocal.get();
        if (vt == null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (t) {
                vt = threadLocal.get();
                if (vt == null) {
                    vt = new VProxyThreadVariable();
                    threadLocal.set(vt);
                }
            }
        }
        return vt;
    }

    VProxyThreadVariable getVariable();

    Thread thread();

    default void start() {
        thread().start();
    }

    default void interrupt() {
        thread().interrupt();
    }

    class VProxyThreadVariable {
        public SelectorEventLoop loop;

        public ArrayParser threadLocalArrayParser;
        public ObjectParser threadLocalObjectParser;
        public StringParser threadLocalStringParser;
        public ArrayParser threadLocalArrayParserJavaObject;
        public ObjectParser threadLocalObjectParserJavaObject;
        public StringParser threadLocalStringParserJavaObject;
        public StringDictionary threadLocalKeyDictionary;

        private static final int XDPChunk_arrayLen = 2048;
        public final long[] XDPChunk_umemArray = new long[XDPChunk_arrayLen];
        public final long[] XDPChunk_chunkArray = new long[XDPChunk_arrayLen];
        public final int[] XDPChunk_refArray = new int[XDPChunk_arrayLen];
        public final int[] XDPChunk_addrArray = new int[XDPChunk_arrayLen];
        public final int[] XDPChunk_endaddrArray = new int[XDPChunk_arrayLen];
        public final int[] XDPChunk_pktaddrArray = new int[XDPChunk_arrayLen];
        public final int[] XDPChunk_pktlenArray = new int[XDPChunk_arrayLen];
        public final PrototypeObjectList<Chunk> XDPChunk_chunkPool = new PrototypeObjectList<>(XDPChunk_arrayLen, Chunk::new);

        public final LRUMap<ByteArray, MacAddress> macCache = new LRUMap<>(1023);
        public final LRUMap<ByteArray, IPv4> ipv4Cache = new LRUMap<>(1023);
        public final LRUMap<ByteArray, IPv6> ipv6Cache = new LRUMap<>(1023);

        public MacAddress getOrCacheMac(ByteArray key) {
            MacAddress mac = macCache.get(key);
            if (mac == null) {
                mac = new MacAddress(key);
                macCache.put(key.persist(), mac);
            }
            return mac;
        }

        public IPv4 getOrCacheIPv4(ByteArray key) {
            IPv4 ip = ipv4Cache.get(key);
            if (ip == null) {
                ip = (IPv4) IP.from(key.toJavaArray());
                ipv4Cache.put(key.persist(), ip);
            }
            return ip;
        }

        public IPv6 getOrCacheIPv6(ByteArray key) {
            IPv6 ip = ipv6Cache.get(key);
            if (ip == null) {
                ip = (IPv6) IP.from(key.toJavaArray());
                ipv6Cache.put(key.persist(), ip);
            }
            return ip;
        }

        public String debugInfo;

        public void newUuidDebugInfo() {
            if (Logger.debugOn()) {
                debugInfo = UUID.randomUUID().toString();
            }
        }
    }
}
