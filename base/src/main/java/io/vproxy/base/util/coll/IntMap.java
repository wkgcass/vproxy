package io.vproxy.base.util.coll;

import java.util.*;

public class IntMap<V> {
    private static final int CHUNK_SIZE = 64;
    private final ArrayList<Range> ranges = new ArrayList<>();
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

        @Override
        public String toString() {
            return "Range[" + initial + ", " + (initial + CHUNK_SIZE) + ")";
        }
    }

    public IntMap() {
    }

    private Range getRange(int n) {
        if (ranges.isEmpty()) {
            return null;
        }
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = ranges.size(); i < size; ++i) {
            var range = ranges.get(i);
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
        if (ranges.isEmpty() || ranges.get(ranges.size() - 1).initial < initial) {
            range = new Range(initial);
            ranges.add(range);
            return range.put(n, value);
        }
        if (ranges.get(0).initial > initial) {
            range = new Range(initial);
            ranges.add(0, range);
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
        throw new Error("should not reach here!!! n=" + n + ", ranges=" + ranges);
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
