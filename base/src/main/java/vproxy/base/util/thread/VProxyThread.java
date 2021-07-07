package vproxy.base.util.thread;

import vjson.parser.ArrayParser;
import vjson.parser.ObjectParser;
import vjson.parser.StringParser;
import vjson.util.StringDictionary;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Logger;

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

        public String debugInfo;

        public void newUuidDebugInfo() {
            if (Logger.debugOn()) {
                debugInfo = UUID.randomUUID().toString();
            }
        }
    }
}
