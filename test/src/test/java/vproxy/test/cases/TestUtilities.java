package vproxy.test.cases;

import org.junit.Test;
import vproxybase.util.objectpool.ConcurrentObjectPool;
import vproxybase.util.objectpool.CursorList;
import vproxybase.util.objectpool.PrototypeObjectPool;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class TestUtilities {
    @Test
    public void noSetToNullArrayList() {
        CursorList<Integer> list = new CursorList<>(4);
        list.add(1);
        list.add(2);
        list.add(3);
        assertEquals(List.of(1, 2, 3), list);
        assertEquals(list, List.of(1, 2, 3));
        assertEquals(4, list.currentCapacity());
        assertEquals(3, list.total());
        int idx = 0;
        for (int i : list) {
            assertEquals(++idx, i);
        }

        list.remove(2);
        assertEquals(List.of(1, 2), list);
        assertEquals(list, List.of(1, 2));
        assertEquals(3, list.total());

        CursorList<Integer> list2 = new CursorList<>();
        list2.add(7);
        list2.add(8);
        list2.add(9);
        assertEquals(List.of(7, 8, 9), list2);
        assertEquals(list2, List.of(7, 8, 9));
        assertEquals(16, list2.currentCapacity());

        list.addAll(list2);
        assertEquals(List.of(1, 2, 7, 8, 9), list);
        assertEquals(list, List.of(1, 2, 7, 8, 9));
        assertEquals(15, list.currentCapacity());
        assertEquals(5, list.total());

        assertEquals(9, list.remove(4).intValue());
        assertEquals(8, list.remove(3).intValue());
        assertEquals(List.of(1, 2, 7), list);
        assertEquals(list, List.of(1, 2, 7));
        assertEquals(5, list.total());

        list.setSize(5);
        assertEquals(List.of(1, 2, 7, 8, 9), list);
        assertEquals(list, List.of(1, 2, 7, 8, 9));
        assertEquals(5, list.total());

        list.store(10);
        assertEquals(6, list.total());
        assertEquals(List.of(1, 2, 7, 8, 9), list);
        assertEquals(list, List.of(1, 2, 7, 8, 9));
        list.setSize(6);
        List<Integer> expected = List.of(1, 2, 7, 8, 9, 10);
        assertEquals(expected, list);
        assertEquals(list, expected);
        idx = 0;
        for (int i : list) {
            assertEquals(expected.get(idx++).intValue(), i);
        }
    }

    @Test
    public void prototypeObjectPool() {
        int[] count = new int[]{0};
        PrototypeObjectPool<Integer> pool = new PrototypeObjectPool<>(4, () -> ++count[0]);
        assertEquals(1, pool.poll().intValue());
        assertEquals(2, pool.poll().intValue());
        assertEquals(3, pool.poll().intValue());
        assertEquals(3, count[0]);
        pool.release(3);
        assertEquals(3, pool.poll().intValue());
        assertEquals(2, pool.poll().intValue());
        assertEquals(1, pool.poll().intValue());
        assertEquals(3, count[0]);

        pool.release(4); // will have the same effect as releasing 3
        assertEquals(3, pool.poll().intValue());
        assertEquals(2, pool.poll().intValue());
        assertEquals(1, pool.poll().intValue());
        assertEquals(3, count[0]);

        assertEquals(4, pool.poll().intValue());
        assertEquals(4, count[0]);
    }

    @Test
    public void concurrentObjectPool() throws Exception {
        ConcurrentObjectPool<Integer> pool = new ConcurrentObjectPool<>(128);
        AtomicInteger atomicInteger = new AtomicInteger(0);
        List<Thread> threads = new LinkedList<>();
        for (int i = 0; i < 256; ++i) {
            Thread t = new Thread(() -> {
                if (atomicInteger.getAndIncrement() % 2 == 0) {
                    pool.add(1);
                } else {
                    pool.poll();
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}
