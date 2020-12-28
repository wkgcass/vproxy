package vproxybase.util.direct;

import sun.misc.Unsafe;
import vproxybase.GlobalInspection;
import vproxybase.util.Logger;
import vproxybase.util.VProxyThread;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

public class DirectMemoryUtils {
    private DirectMemoryUtils() {
    }

    private static final Unsafe U;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            U = (Unsafe) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Logger.shouldNotHappen("Reflection failure: get unsafe failed " + e);
            throw new RuntimeException(e);
        }
    }

    public static DirectByteBuffer allocateDirectBuffer(int size) {
        Thread cur = Thread.currentThread();
        if (cur instanceof VProxyThread) {
            DirectByteBuffer directByteBuffer = ((VProxyThread) cur).getVariable().getBufferCache(size);
            if (directByteBuffer != null) {
                assert Logger.lowLevelDebug("cached direct buffer retrieved: " + size);
                directByteBuffer.limit(directByteBuffer.capacity()).position(0);
                return directByteBuffer;
            }
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(size);
        GlobalInspection.getInstance().directBufferAllocate(buf.capacity());
        return new DirectByteBuffer(buf);
    }

    static boolean free(DirectByteBuffer buffer, boolean tryCache) {
        assert Logger.lowLevelDebug("run Utils.clean");
        if (!buffer.realBuffer().isDirect()) {
            assert Logger.lowLevelDebug("not direct buffer");
            return true; // return true because it does not need cleaning
        }
        if (tryCache) {
            Thread cur = Thread.currentThread();
            if (cur instanceof VProxyThread) {
                boolean succeeded = ((VProxyThread) cur).getVariable().releaseBufferCache(buffer);
                if (succeeded) {
                    assert Logger.lowLevelDebug("direct buffer cached: " + buffer.capacity());
                    return false;
                }
            }
        }
        assert Logger.lowLevelDebug("is direct buffer, do clean");
        U.invokeCleaner(buffer.realBuffer());
        GlobalInspection.getInstance().directBufferFree(buffer.capacity());
        return true;
    }
}
