package vproxy.base.util;

import java.util.*;

public class IntMap<V> {
    private static final int CHUNK_SIZE = 8192;
    private final LinkedList<Range> ranges = new LinkedList<>();
    private final Set<Integer> keySet = new HashSet<>();
    private final LinkedList<V> values = new LinkedList<>();

    private class Range {
        final int initial;
        Object[] array = new Object[CHUNK_SIZE];

        private Range(int initial) {
            this.initial = initial;
        }

        public V get(int index) {
            //noinspection unchecked
            return (V) array[index % CHUNK_SIZE];
        }

        public V put(int index, V value) {
            int n = index % CHUNK_SIZE;
            //noinspection unchecked
            V old = (V) array[n];
            array[n] = value;

            keySet.add(index);
            values.add(value);

            return old;
        }

        public V remove(int index) {
            int n = index % CHUNK_SIZE;
            //noinspection unchecked
            V old = (V) array[n];
            array[n] = null;

            keySet.remove(index);
            values.remove(old);

            return old;
        }

        public boolean contains(int value) {
            return initial <= value && value < initial + CHUNK_SIZE;
        }

        public boolean isEmpty() {
            for (Object o : array) {
                if (o != null) return false;
            }
            return true;
        }
    }

    public IntMap() {
    }

    private Range getRange(int n) {
        if (ranges.isEmpty()) {
            return null;
        }
        if (ranges.getFirst().contains(n)) {
            return ranges.getFirst();
        }
        if (ranges.size() == 1) {
            return null; // already searched the whole list because there's only one range chunk
        }
        if (ranges.getLast().contains(n)) {
            return ranges.getLast();
        }
        var ite = ranges.iterator();
        ite.next(); // the first range already searched, so skip
        while (ite.hasNext()) {
            var range = ite.next();
            if (range.contains(n)) {
                return range;
            }
        }
        return null;
    }

    public boolean containsKey(int n) {
        var range = getRange(n);
        return range != null && range.get(n) != null;
    }

    public V get(int n) {
        var range = getRange(n);
        if (range == null) return null;
        return range.get(n);
    }

    public V put(int n, V value) {
        if (value == null) {
            throw new IllegalArgumentException("null is not allowed");
        }

        var range = getRange(n);
        if (range != null) return range.put(n, value);
        int initial = (n / CHUNK_SIZE) * CHUNK_SIZE;
        if (ranges.isEmpty() || ranges.getLast().initial < initial) {
            range = new Range(initial);
            ranges.add(range);
            return range.put(n, value);
        }
        var ite = ranges.listIterator();
        while (ite.hasNext()) {
            range = ite.next();
            if (initial > range.initial) {
                ite.previous();
                range = new Range(initial);
                ite.add(range);
                return range.put(n, value);
            }
        }
        // should not reach here
        throw new Error("should not reach here!!!");
    }

    public V remove(int n) {
        var range = getRange(n);
        if (range == null) return null;
        V old = range.remove(n);

        if (range.isEmpty()) {
            ranges.remove(range);
        }

        return old;
    }

    public Set<Integer> keySet() {
        return Collections.unmodifiableSet(keySet);
    }

    public Collection<V> values() {
        return Collections.unmodifiableCollection(values);
    }
}
