package io.vproxy.base.util.coll;

import java.util.Iterator;

public class RingQueue<E> implements Iterable<E> {
    private Object[] array;
    private boolean eAfterS = true;
    private int start = 0;
    private int end = 0;
    private final ExpandFunction expandFunction;

    public RingQueue() {
        this(16);
    }

    public RingQueue(ExpandFunction e) {
        this(16, e);
    }

    public RingQueue(int initialCapacity) {
        this(initialCapacity, n -> n + 10);
    }

    public RingQueue(int initialCapacity, ExpandFunction e) {
        this.array = new Object[initialCapacity];
        this.expandFunction = e;
    }

    @FunctionalInterface
    public interface ExpandFunction {
        int expand(int current);
    }

    private E get(int idx) {
        //noinspection unchecked
        return (E) array[idx];
    }

    private void expand() {
        int newLen = expandFunction.expand(array.length);
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

    public E peek() {
        if (eAfterS) {
            if (start == end) {
                return null;
            }
        }
        return get(start);
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

    public E last() {
        if (eAfterS) {
            if (start == end) {
                return null;
            }
        }
        if (end == 0) return get(array.length - 1);
        return get(end - 1);
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

    @Override
    public Iterator<E> iterator() {
        return new RingQueueIterator<>();
    }

    private class RingQueueIterator<E> implements Iterator<E> {
        private int nextIndex = start;
        private int step = 0;

        @Override
        public boolean hasNext() {
            if (eAfterS) {
                return nextIndex < end;
            } else if (step == 0) {
                return nextIndex < array.length;
            } else {
                return nextIndex < end;
            }
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new IndexOutOfBoundsException();
            }
            //noinspection unchecked
            E e = (E) array[nextIndex];
            nextIndex++;
            if (!eAfterS) {
                if (nextIndex == array.length) {
                    nextIndex = 0;
                }
            }
            return e;
        }
    }
}
