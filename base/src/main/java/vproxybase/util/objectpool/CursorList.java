package vproxybase.util.objectpool;

import java.util.AbstractList;
import java.util.RandomAccess;

public class CursorList<E> extends AbstractList<E> implements RandomAccess {
    private Object[] elementData;
    private int size = 0;
    private int total = 0;

    public CursorList() {
        this(16);
    }

    public CursorList(int initialCapacity) {
        this.elementData = new Object[initialCapacity];
    }

    @Override
    public E get(int index) {
        //noinspection unchecked
        return (E) elementData[index];
    }

    private void ensureCapacity(int cap) {
        if (elementData.length >= cap) {
            return;
        }
        Object[] arr = new Object[cap + 10];
        System.arraycopy(elementData, 0, arr, 0, elementData.length);
        elementData = arr;
    }

    @Override
    public boolean add(E element) {
        ensureCapacity(size + 1);

        elementData[size] = element;
        ++size;
        if (size > total) {
            total = size;
        }
        return true;
    }

    public void store(E element) {
        ensureCapacity(total + 1);

        elementData[total] = element;
        ++total;
    }

    public void addAll(CursorList<E> ls) {
        ensureCapacity(size + ls.size);
        System.arraycopy(ls.elementData, 0, this.elementData, size, ls.size);
        size += ls.size;
        if (size > total) {
            total = size;
        }
    }

    @Override
    public E remove(int index) {
        if (index != size - 1) {
            throw new UnsupportedOperationException();
        }
        //noinspection unchecked
        E e = (E) elementData[index];
        --size;
        return e;
    }

    @Override
    public int size() {
        return size;
    }

    public int total() {
        return total;
    }

    public int currentCapacity() {
        return elementData.length;
    }

    public void setSize(int size) {
        if (size > total) {
            throw new IndexOutOfBoundsException("size(" + size + ") > total(" + total + ")");
        }
        this.size = size;
    }

    @Override
    public void clear() {
        size = 0;
    }
}
