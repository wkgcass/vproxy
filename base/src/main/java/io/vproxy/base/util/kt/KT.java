package io.vproxy.base.util.kt;

import kotlin.jvm.internal.Reflection;
import kotlin.reflect.KClass;

public class KT {
    private KT() {
    }

    public static <T> KClass<T> kclass(Class<T> cls) {
        //noinspection unchecked
        return Reflection.getOrCreateKotlinClass(cls);
    }
}
