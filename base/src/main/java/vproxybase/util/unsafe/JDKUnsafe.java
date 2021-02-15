package vproxybase.util.unsafe;

import vproxybase.util.LogType;
import vproxybase.util.Logger;

public interface JDKUnsafe {

    static byte[] allocateUninitializedByteArray(int len) {
        return JDKUnsafeHolder.getUnsafe().allocateUninitialized0(len);
    }

    byte[] allocateUninitialized0(int len);
}

class JDKUnsafeHolder {
    private static JDKUnsafe UNSAFE;

    static {
        try {
            UNSAFE = (JDKUnsafe) Class.forName("vproxybase.util.unsafe.JDKUnsafeImpl")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Throwable e) {
            Logger.warn(LogType.ALERT, "Reflection failure: you may add JDK startup option '--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED' to enable JDKUnsafe.\nThe exception is " + e);
            UNSAFE = new JDKUnsafeFallback();
        }
    }

    public static JDKUnsafe getUnsafe() {
        return UNSAFE;
    }
}
