package io.vproxy.base.util.functional;

@FunctionalInterface
public interface BooleanFunction<T> {
    T apply(boolean b);
}
