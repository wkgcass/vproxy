package vproxy.vlibbase;

import java.io.IOException;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface Handler<T> extends BiConsumer<IOException, T> {
    default void accept(IOException e) {
        accept(e, null);
    }

    default void accept(T t) {
        accept(null, t);
    }
}
