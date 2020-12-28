package vproxybase.util;

import vproxybase.GlobalInspection;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.direct.DirectByteBuffer;
import vproxybase.util.table.TR;

import java.util.ArrayList;
import java.util.Arrays;

public class VProxyThread extends Thread {
    private static final ThreadLocal<VProxyThreadVariable> threadLocal = new ThreadLocal<>();

    public static final class VProxyThreadVariable {
        public SelectorEventLoop loop;

        private static final int BUF_POOL_SIZE = 32;
        private final ArrayList<DirectByteBuffer> _1 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _2 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _4 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _8 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _16 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _32 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _64 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _128 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _256 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _512 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _1024 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _2048 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _4096 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _8192 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _16384 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _24576 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _32768 = new ArrayList<>(BUF_POOL_SIZE);
        private final ArrayList<DirectByteBuffer> _65536 = new ArrayList<>(BUF_POOL_SIZE);

        public DirectByteBuffer getBufferCache(int size) {
            switch (size) {
                case 1:
                    return getBufferCache(_1);
                case 2:
                    return getBufferCache(_2);
                case 4:
                    return getBufferCache(_4);
                case 8:
                    return getBufferCache(_8);
                case 16:
                    return getBufferCache(_16);
                case 32:
                    return getBufferCache(_32);
                case 64:
                    return getBufferCache(_64);
                case 128:
                    return getBufferCache(_128);
                case 256:
                    return getBufferCache(_256);
                case 512:
                    return getBufferCache(_512);
                case 1024:
                    return getBufferCache(_1024);
                case 2048:
                    return getBufferCache(_2048);
                case 4096:
                    return getBufferCache(_4096);
                case 8192:
                    return getBufferCache(_8192);
                case 16384:
                    return getBufferCache(_16384);
                case 24576:
                    return getBufferCache(_24576);
                case 32768:
                    return getBufferCache(_32768);
                case 65536:
                    return getBufferCache(_65536);
                default:
                    return null;
            }
        }

        private DirectByteBuffer getBufferCache(ArrayList<DirectByteBuffer> buffers) {
            int size = buffers.size();
            if (size == 0) {
                return null;
            }
            DirectByteBuffer b = buffers.remove(size - 1);
            GlobalInspection.getInstance().directBufferTakeFromCache(b.capacity());
            return b;
        }

        public boolean releaseBufferCache(DirectByteBuffer buf) {
            switch (buf.capacity()) {
                case 1:
                    return releaseBufferCache(_1, buf);
                case 2:
                    return releaseBufferCache(_2, buf);
                case 4:
                    return releaseBufferCache(_4, buf);
                case 8:
                    return releaseBufferCache(_8, buf);
                case 16:
                    return releaseBufferCache(_16, buf);
                case 32:
                    return releaseBufferCache(_32, buf);
                case 64:
                    return releaseBufferCache(_64, buf);
                case 128:
                    return releaseBufferCache(_128, buf);
                case 256:
                    return releaseBufferCache(_256, buf);
                case 512:
                    return releaseBufferCache(_512, buf);
                case 1024:
                    return releaseBufferCache(_1024, buf);
                case 2048:
                    return releaseBufferCache(_2048, buf);
                case 4096:
                    return releaseBufferCache(_4096, buf);
                case 8192:
                    return releaseBufferCache(_8192, buf);
                case 16384:
                    return releaseBufferCache(_16384, buf);
                case 24576:
                    return releaseBufferCache(_24576, buf);
                case 32768:
                    return releaseBufferCache(_32768, buf);
                case 65536:
                    return releaseBufferCache(_65536, buf);
                default:
                    return false;
            }
        }

        private boolean releaseBufferCache(ArrayList<DirectByteBuffer> buffers, DirectByteBuffer buf) {
            if (buffers.size() == BUF_POOL_SIZE) {
                return false;
            }
            buffers.add(buf);
            GlobalInspection.getInstance().directBufferCache(buf.capacity());
            return true;
        }

        public void release() {
            for (var ls : Arrays.asList(
                _1, _2, _4, _8, _16, _32, _64,
                _128, _256, _512,
                _1024, _2048, _4096, _8192,
                _16384, _24576, _32768, _65536
            )) {
                for (var b : ls) {
                    GlobalInspection.getInstance().directBufferTakeFromCache(b.capacity());
                    b.clean(false);
                }
            }
        }

        public void inspectCachedBuffers(TR tr) {
            tr
                .td("" + _1.size()).td("" + _2.size()).td("" + _4.size()).td("" + _8.size())
                .td("" + _16.size()).td("" + _32.size()).td("" + _64.size())
                .td("" + _128.size()).td("" + _256.size()).td("" + _512.size())
                .td("" + _1024.size()).td("" + _2048.size()).td("" + _4096.size()).td("" + _8192.size())
                .td("" + _16384.size()).td("" + _24576.size()).td("" + _32768.size()).td("" + _65536.size());
        }
    }

    private final VProxyThreadVariable variable = new VProxyThreadVariable();

    public VProxyThread(Runnable runnable, String name) {
        super(GlobalInspection.getInstance().wrapThread(runnable), name);
    }

    public VProxyThreadVariable getVariable() {
        return variable;
    }

    public static VProxyThreadVariable current() {
        Thread t = Thread.currentThread();
        if (t instanceof VProxyThread) {
            return ((VProxyThread) t).variable;
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
}
