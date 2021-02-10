package vproxybase.util.unsafe;

import vproxybase.util.Logger;

public interface JDKUnsafe {

    static byte[] allocateUninitializedByteArray(int len) {
        return JDKUnsafeHolder.getUnsafe().allocateUninitialized0(len);
    }

    byte[] allocateUninitialized0(int len);

    class JDKUnsafeHolder {
        private static JDKUnsafe UNSAFE;

        static {
            try {
                UNSAFE = (JDKUnsafeImpl) Class.forName("vproxybase.util.unsafe.JDKUnsafeImpl")
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Throwable e) {
                Logger.alert("Reflection failure: new JDKUnsafeImpl " + e);
                UNSAFE = new JDKUnsafeFallback();
            }
        }

        public static JDKUnsafe getUnsafe() {
            return UNSAFE;
        }
    }
}
