package io.vproxy.base.util.thread;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.objectpool.PrototypeObjectList;
import vjson.parser.ArrayParser;
import vjson.parser.ObjectParser;
import vjson.parser.StringParser;
import vjson.util.StringDictionary;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIEnv;
import io.vproxy.xdp.Chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.UUID;
import java.util.function.BooleanSupplier;

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

        public static final int XDPChunk_arrayLen = 2048;
        private final Arena XDPChunkArena = Arena.ofConfined();
        public final MemorySegment /*long[]*/ XDPChunk_umemArray = XDPChunkArena.allocate(ValueLayout.JAVA_LONG.byteSize() * XDPChunk_arrayLen);
        public final MemorySegment /*long[]*/ XDPChunk_chunkArray = XDPChunkArena.allocate(ValueLayout.JAVA_LONG.byteSize() * XDPChunk_arrayLen);
        public final MemorySegment /*int[]*/ XDPChunk_refArray = XDPChunkArena.allocate(ValueLayout.JAVA_INT.byteSize() * XDPChunk_arrayLen);
        public final MemorySegment /*int[]*/ XDPChunk_addrArray = XDPChunkArena.allocate(ValueLayout.JAVA_INT.byteSize() * XDPChunk_arrayLen);
        public final MemorySegment /*int[]*/ XDPChunk_endaddrArray = XDPChunkArena.allocate(ValueLayout.JAVA_INT.byteSize() * XDPChunk_arrayLen);
        public final MemorySegment /*int[]*/ XDPChunk_pktaddrArray = XDPChunkArena.allocate(ValueLayout.JAVA_INT.byteSize() * XDPChunk_arrayLen);
        public final MemorySegment /*int[]*/ XDPChunk_pktlenArray = XDPChunkArena.allocate(ValueLayout.JAVA_INT.byteSize() * XDPChunk_arrayLen);
        public final PrototypeObjectList<Chunk> XDPChunk_chunkPool = new PrototypeObjectList<>(XDPChunk_arrayLen, Chunk::new);

        private PNIEnv pniEnv;

        public String debugInfo;

        public PNIEnv getEnv() {
            if (pniEnv == null) {
                pniEnv = new PNIEnv(Allocator.ofConfined());
            }
            return pniEnv;
        }

        public void newUuidDebugInfo() {
            assert ((BooleanSupplier) (() -> {
                if (Logger.debugOn()) {
                    debugInfo = UUID.randomUUID().toString();
                }
                return true;
            })).getAsBoolean();
        }
    }
}
