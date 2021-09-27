package io.vproxy.base.util.anno;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
public @interface ThreadSafe {
    boolean value() default true;
}
