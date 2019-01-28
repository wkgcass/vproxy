package net.cassite.vproxy.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
public @interface ThreadSafe {
    boolean value() default true;
}
