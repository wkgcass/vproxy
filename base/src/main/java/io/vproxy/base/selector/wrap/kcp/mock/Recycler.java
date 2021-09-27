package io.vproxy.base.selector.wrap.kcp.mock;

public abstract class Recycler<E> {
    public static class Handle<E> {
        public void recycle(E e) {
            // do nothing
        }
    }

    abstract protected E newObject(Handle<E> handle);

    public E get() {
        return newObject(new Handle<>());
    }
}
