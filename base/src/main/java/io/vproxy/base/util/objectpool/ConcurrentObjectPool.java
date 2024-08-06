package io.vproxy.base.util.objectpool;

import io.vproxy.base.util.Utils;
import io.vproxy.base.util.lock.ReadWriteSpinLock;
import io.vproxy.base.util.thread.VProxyThread;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The pool is split into a few partitions, each partition has a read array and a write array.
 * When adding, elements will be added into the write array.
 * When polling, elements will be polled from the read array.
 * If read array is empty and write array is full, and when running polling or adding, the two arrays will be swapped
 * The arrays will not be operated when they are being swapped.
 * When concurrency occurs, the operations will retry for maximum 10 times.
 *
 * @param <E> element type
 */
public class ConcurrentObjectPool<E> {
    private final int partitionCount;
    private final int partitionCountMinusOne;
    private final Partition<E>[] partitions;
    private final int maxTraversal;

    public ConcurrentObjectPool(int capacityHint) {
        this(capacityHint, 16, 0);
    }

    public ConcurrentObjectPool(int capacityHint, int partitionCountHint, int maxTraversalHint) {
        capacityHint = Utils.minPow2GreaterThan(capacityHint) / 2;
        partitionCountHint = Utils.minPow2GreaterThan(partitionCountHint);

        if (capacityHint / partitionCountHint == 0) {
            partitionCount = 1;
        } else {
            partitionCount = partitionCountHint;
        }
        partitionCountMinusOne = partitionCount - 1;

        //noinspection unchecked
        this.partitions = new Partition[partitionCount];
        for (int i = 0; i < partitionCount; ++i) {
            partitions[i] = new Partition<>(capacityHint / partitionCount);
        }

        if (maxTraversalHint <= 0 || maxTraversalHint >= partitionCount) {
            maxTraversal = partitionCount;
        } else {
            maxTraversal = maxTraversalHint;
        }
    }

    private int hashForPartition() {
        var tid = VProxyThread.current().threadId;
        return (int) (tid & partitionCountMinusOne);
    }

    public boolean add(E e) {
        int m = maxTraversal;
        int hash = hashForPartition();
        for (int i = hash; m > 0; ++i, --m) {
            if (partitions[i & partitionCountMinusOne].add(e)) {
                return true;
            }
        }
        return false;
    }

    public E poll() {
        int m = maxTraversal;
        int hash = hashForPartition();
        for (int i = hash; m > 0; ++i, --m) {
            E e = partitions[i & partitionCountMinusOne].poll();
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    public int size() {
        int size = 0;
        for (int i = 0; i < partitionCount; ++i) {
            size += partitions[i].size();
        }
        return size;
    }

    private static class Partition<E> {
        private final ReadWriteSpinLock lock = new ReadWriteSpinLock();
        private volatile ArrayQueue<E> read;
        private volatile ArrayQueue<E> write;
        private final ArrayQueue<E> _1;
        private final ArrayQueue<E> _2;

        public Partition(int capacity) {
            _1 = new ArrayQueue<>(capacity, lock);
            _2 = new ArrayQueue<>(capacity, lock);
            read = _1;
            write = _2;
        }

        public boolean add(E e) {
            return add(1, e);
        }

        private boolean add(int retry, E e) {
            if (retry > 10) { // max retry for 10 times
                return false; // too many retries
            }

            var write = this.write;
            if (write.add(e)) {
                return true;
            }

            // the $write is full now
            if (swap(read, write, false)) {
                return add(retry + 1, e);
            }
            return false;
        }

        public E poll() {
            return poll(1);
        }

        private E poll(int retry) {
            if (retry > 10) { // max retry for 10 times
                return null; // too many retries
            }

            var read = this.read;
            var write = this.write;

            var ret = read.poll();
            if (ret != null) {
                return ret;
            }

            // no elements in the $read now
            if (swap(read, write, true)) {
                return poll(retry + 1);
            }
            return null;
        }

        // return true -> need retry
        // return false -> failed and should not retry
        private boolean swap(ArrayQueue<E> read, ArrayQueue<E> write, boolean isPolling) {
            // check whether we can swap
            if (read == write) {
                // is being swapped by another thread
                return true;
            }

            if (isPolling) { // $read is empty
                int writeEnd = write.end.get();
                if (writeEnd < write.capacity) {
                    return false; // capacity not reached, do not swap and return nothing
                    // no retry here because the write array will not change until something written into it
                }
            } else { // $write is full
                int readStart = read.start.get();
                if (readStart < read.end.get()) {
                    return false; // still have objects to fetch, do not swap
                    // no retry here because the read array will not change until something polling from it
                }
            }

            lock.writeLock();
            if (this.read != read) {
                // already swapped by another thread
                lock.writeUnlock();
                return true;
            }
            // we can safely swap the two arrays now
            this.read = write;
            // the $read is expected to be empty
            assert read.size() == 0;
            read.reset(); // reset the cursors, so further operations can store data into this array
            this.write = read; // swapping is done
            lock.writeUnlock();

            return true;
        }

        public int size() {
            return _1.size() + _2.size();
        }
    }

    private static class ArrayQueue<E> {
        private final int capacity;
        private final ReadWriteSpinLock lock;
        private final AtomicReferenceArray<E> array;
        private final AtomicInteger start = new AtomicInteger(0);
        private final AtomicInteger end = new AtomicInteger(0);

        private ArrayQueue(int capacity, ReadWriteSpinLock lock) {
            this.capacity = capacity;
            this.lock = lock;
            this.array = new AtomicReferenceArray<>(capacity);
        }

        boolean add(E e) {
            lock.readLock();

            if (end.get() >= capacity) {
                lock.readUnlock();
                return false; // exceeds capacity
            }
            int index = end.getAndIncrement();
            if (index < capacity) {
                // storing should succeed
                array.set(index, e);
                lock.readUnlock();
                return true;
            } else {
                // storing failed
                lock.readUnlock();
                return false;
            }
        }

        E poll() {
            lock.readLock();

            if (start.get() >= end.get() || start.get() >= capacity) {
                lock.readUnlock();
                return null;
            }
            int idx = start.getAndIncrement();
            if (idx >= end.get() || idx >= capacity) {
                lock.readUnlock();
                return null; // concurrent polling
            }
            var e = array.get(idx);
            lock.readUnlock();
            return e;
        }

        int size() {
            int start = this.start.get();
            if (start >= capacity) {
                return 0;
            }
            int cap = end.get();
            if (cap > capacity) {
                cap = capacity;
            }
            if (start > cap) {
                return 0;
            }
            return cap - start;
        }

        void reset() {
            end.set(0);
            start.set(0);
        }
    }
}
