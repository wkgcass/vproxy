package vproxybase.util.direct;

import sun.misc.Unsafe;
import vproxybase.GlobalInspection;
import vproxybase.util.Logger;

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
        ByteBuffer buf = ByteBuffer.allocateDirect(size);
        GlobalInspection.getInstance().directBufferAllocate(buf.capacity());
        return new DirectByteBuffer(buf);
    }

    static void free(ByteBuffer buffer) {
        assert Logger.lowLevelDebug("run Utils.clean");
        if (!buffer.getClass().getName().equals("java.nio.DirectByteBuffer")) {
            assert Logger.lowLevelDebug("not direct buffer");
            return;
        }
        assert Logger.lowLevelDebug("is direct buffer, do clean");
        U.invokeCleaner(buffer);
        GlobalInspection.getInstance().directBufferFree(buffer.capacity());
    }
}
