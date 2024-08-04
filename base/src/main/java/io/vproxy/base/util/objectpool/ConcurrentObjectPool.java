package io.vproxy.base.util.objectpool;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The pool is split into a few partitions, each partition has a read array and a write array.
 * When adding, elements will be added into the write array.
 * When polling, elements will be polled from the read array.
 * If read array is empty and write array is full, and when running polling, the two arrays will be swapped
 * (they will not be swapped when adding).
 * The arrays will not be operated when they are being swapped.
 * When concurrency occurs, the operations will retry for maximum 10 times.
 *
 * @param <E> element type
 */
public class ConcurrentObjectPool<E> {
    private final int partitionCount;
    private final Partition<E>[] partitions;

    public ConcurrentObjectPool(int capacityHint) {
        this(capacityHint, 16, 4);
    }

    public ConcurrentObjectPool(int capacityHint, int partitionCountHint, int minPartitionCapHint) {
        capacityHint -= 1;
        capacityHint |= capacityHint >>> 1;
        capacityHint |= capacityHint >>> 2;
        capacityHint |= capacityHint >>> 4;
        capacityHint |= capacityHint >>> 8;
        capacityHint |= capacityHint >>> 16;
        capacityHint += 1;

        if (capacityHint / minPartitionCapHint == 0) {
            partitionCount = 1;
        } else {
            partitionCount = Math.min(capacityHint / minPartitionCapHint, partitionCountHint);
        }

        //noinspection unchecked
        this.partitions = new Partition[partitionCount];
        for (int i = 0; i < partitionCount; ++i) {
            partitions[i] = new Partition<>(capacityHint / partitionCount);
        }
    }

    public boolean add(E e) {
        for (int i = 0; i < partitionCount; ++i) {
            if (partitions[i].add(e)) {
                return true;
            }
        }
        return false;
    }

    public E poll() {
        for (int i = 0; i < partitionCount; ++i) {
            E e = partitions[i].poll();
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
        private final AtomicReference<StorageArray<E>> read;
        private volatile StorageArray<E> write;
        private final StorageArray<E> _1;
        private final StorageArray<E> _2;

        public Partition(int capacity) {
            _1 = new StorageArray<>(capacity);
            _2 = new StorageArray<>(capacity);
            read = new AtomicReference<>(_1);
            write = _2;
        }

        public boolean add(E e) {
            StorageArray<E> write = this.write;

            // adding is always safe
            //noinspection RedundantIfStatement
            if (write.add(e)) {
                return true;
            }
            // $write is full, storing fails
            return false;
        }

        public E poll() {
            return poll(1);
        }

        private E poll(int retry) {
            if (retry > 10) { // max retry for 10 times
                return null; // too many retries
            }

            StorageArray<E> read = this.read.get();
            StorageArray<E> write = this.write;

            // polling is always safe
            E ret = read.poll();
            if (ret != null) {
                return ret;
            }

            // no elements in the $read now
            // check whether we can swap (whether $write is full)

            int writeEnd = write.end.get();
            if (writeEnd < write.capacity) {
                return null; // capacity not reached, do not swap and return nothing
                // no retry here because the write array will not change until something written into it
            }
            // also we should check whether there are no elements being stored
            if (write.storing.get() != 0) { // element is being stored into the array
                return poll(retry + 1); // try again
            }
            // now we can know that writing operations will not happen in this partition

            // we can safely swap the two arrays now
            if (!this.read.compareAndSet(read, write)) {
                return poll(retry + 1); // concurrency detected: another thread is swapping
            }
            // the $read is expected to be empty
            assert read.size() == 0;
            read.reset(); // reset the cursors, so further operations can store data into this array
            this.write = read; // swapping is done
            return poll(retry + 1); // poll again
        }

        public int size() {
            return _1.size() + _2.size();
        }
    }

    private static class StorageArray<E> {
        private final int capacity;
        private final AtomicReferenceArray<E> array;
        private final AtomicInteger start = new AtomicInteger(-1);
        private final AtomicInteger end = new AtomicInteger(-1);
        private final AtomicInteger storing = new AtomicInteger(0);

        private StorageArray(int capacity) {
            this.capacity = capacity;
            this.array = new AtomicReferenceArray<>(capacity);
        }

        boolean add(E e) {
            storing.incrementAndGet();

            if (end.get() >= capacity) {
                storing.decrementAndGet();
                return false; // exceeds capacity
            }
            int index = end.incrementAndGet();
            if (index < capacity) {
                // storing should succeed
                array.set(index, e);
                storing.decrementAndGet();
                return true;
            } else {
                // storing failed
                storing.decrementAndGet();
                return false;
            }
        }

        E poll() {
            if (start.get() + 1 >= end.get() || start.get() + 1 >= capacity) {
                return null;
            }
            int idx = start.incrementAndGet();
            if (idx >= end.get() || idx >= capacity) {
                return null; // concurrent polling
            }
            return array.get(idx);
        }

        int size() {
            int start = this.start.get() + 1;
            if (start >= capacity) {
                return 0;
            }
            int cap = end.get() + 1;
            if (cap > capacity) {
                cap = capacity;
            }
            return cap - start;
        }

        void reset() {
            end.set(-1);
            start.set(-1);
        }
    }
}
