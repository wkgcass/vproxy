package vproxy.base.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashSet<E> implements Set<E> {
    private static final Object _VALUE_ = new Object();
    private final ConcurrentHashMap<E, Object> map = new ConcurrentHashMap<>();

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    @Override
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public Object[] toArray() {
        return new HashSet<>(this).toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        //noinspection SuspiciousToArrayCall
        return new HashSet<>(this).toArray(a);
    }

    @Override
    public boolean add(E e) {
        return map.put(e, _VALUE_) == null;
    }

    @Override
    public boolean remove(Object o) {
        return map.remove(o) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return map.keySet().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean b = false;
        for (E e : c) {
            b |= add(e);
        }
        return b;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Iterator<E> ite = iterator();
        boolean ret = false;
        while (ite.hasNext()) {
            E e = ite.next();
            if (!c.contains(e)) {
                ite.remove();
                ret = true;
            }
        }
        return ret;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean b = false;
        for (Object o : c) {
            b |= remove(o);
        }
        return b;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean isFirst = true;
        for (E e : map.keySet()) {
            if (isFirst) {
                isFirst = false;
            } else {
                sb.append(", ");
            }
            sb.append(e);
        }
        sb.append("]");
        return sb.toString();
    }
}
