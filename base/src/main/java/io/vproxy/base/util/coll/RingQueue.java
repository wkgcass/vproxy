package vproxy.base.util.coll;

public class RingQueue<E> {
    private Object[] array;
    private boolean eAfterS = true;
    private int start = 0;
    private int end = 0;

    public RingQueue() {
        this(16);
    }

    public RingQueue(int initialCapacity) {
        this.array = new Object[initialCapacity];
    }

    private E get(int idx) {
        //noinspection unchecked
        return (E) array[idx];
    }

    private void expand() {
        int newLen = array.length + 10;
        Object[] arr = new Object[newLen];
        if (eAfterS) {
            System.arraycopy(array, start, arr, 0, end - start);
            end = end - start;
        } else {
            System.arraycopy(array, start, arr, 0, array.length - start);
            if (end > 0) {
                System.arraycopy(array, 0, arr, array.length - start, end);
            }
            end = array.length - start + end;
        }
        start = 0;
        array = arr;
        eAfterS = true;
    }

    public void clear() {
        eAfterS = true;
        start = 0;
        end = 0;
    }

    public int size() {
        if (eAfterS) {
            return end - start;
        } else {
            return array.length - start + end;
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int currentCapacity() {
        return array.length;
    }

    public void add(E e) {
        if (eAfterS) {
            array[end] = e;
            if (end == array.length - 1) {
                end = 0;
                eAfterS = false;
            } else {
                ++end;
            }
        } else {
            if (end == start) {
                expand();
            }
            array[end++] = e;
        }
    }

    public E poll() {
        if (eAfterS) {
            if (start == end) {
                return null;
            }
            E e = get(start++);
            if (start == end) {
                clear();
            }
            return e;
        } else {
            E e = get(start);
            if (start == array.length - 1) {
                start = 0;
                eAfterS = true;
            } else {
                ++start;
            }
            return e;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean isFirst = true;
        if (eAfterS) {
            for (int i = start; i < end; ++i) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    sb.append(", ");
                }
                sb.append(array[i]);
            }
        } else {
            for (int i = start; i < array.length; ++i) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    sb.append(", ");
                }
                sb.append(array[i]);
            }
            for (int i = 0; i < end; ++i) {
                sb.append(", ");
                sb.append(array[i]);
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
