package io.vproxy.base.util.objectpool;

import java.util.function.Supplier;

public class PrototypeObjectList<E> extends CursorList<E> {
    private final Supplier<E> constructor;

    public PrototypeObjectList(int capacity, Supplier<E> constructor) {
        super(capacity);
        this.constructor = constructor;
    }

    public E add() {
        int size = size();
        int total = total();
        if (size < total) {
            setSize(size + 1);
            return get(size);
        }
        E e = constructor.get();
        add(e);
        return e;
    }

    @Override
    public E poll() {
        E e = super.poll();
        if (e == null) {
            return constructor.get();
        }
        return e;
    }
}
