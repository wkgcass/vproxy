package vproxybase.util;

import java.util.AbstractList;
import java.util.List;

public class AppendingList<E> extends AbstractList<E> {
    private final List<E> pre;
    private final E value;
    private final int size;

    public AppendingList(List<E> pre, E value) {
        this.pre = pre;
        this.value = value;
        this.size = pre.size() + 1;
    }

    @Override
    public E get(int index) {
        if (size == index + 1) {
            return value;
        } else if (index < size) {
            return pre.get(index);
        } else {
            throw new IndexOutOfBoundsException("" + index);
        }
    }

    @Override
    public int size() {
        return size;
    }
}
