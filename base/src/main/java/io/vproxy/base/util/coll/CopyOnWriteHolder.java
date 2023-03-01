package io.vproxy.base.util.coll;

import java.util.function.Consumer;
import java.util.function.Function;

public class CopyOnWriteHolder<E> {
    private E e;

    private final Function<E, E> copyFunc;

    public CopyOnWriteHolder(E initialValue, Function<E, E> copyFunc) {
        this.e = initialValue;
        this.copyFunc = copyFunc;
    }

    public E get() {
        return e;
    }

    public synchronized void update(Consumer<E> func) {
        E newE = copyFunc.apply(e);
        func.accept(newE);
        e = newE;
    }
}
