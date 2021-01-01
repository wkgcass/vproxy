package vproxybase.util.objectpool;

import java.util.function.Supplier;

public class PrototypeObjectPool<E> {
    private final int capacity;
    private final CursorList<E> pool;
    private final Supplier<E> constructor;

    public PrototypeObjectPool(int capacity, Supplier<E> constructor) {
        this.capacity = capacity;
        this.pool = new CursorList<>(capacity);
        this.constructor = constructor;
    }

    public E poll() {
        int size = pool.size();
        if (size == 0) {
            E e = constructor.get();
            pool.store(e);
            return e;
        }
        return pool.remove(size - 1);
    }

    public void release(int count) {
        int total = pool.total();
        int size = pool.size();
        if (size + count > capacity) {
            // store no more than capacity objects
            count = capacity - size;
        }
        if (total - size < count) {
            pool.setSize(total);
        } else {
            pool.setSize(size + count);
        }
    }
}
