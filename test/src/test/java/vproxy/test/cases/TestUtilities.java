package vproxy.test.cases;

import org.junit.Test;
import vproxy.base.util.coll.RingQueue;
import vproxy.base.util.display.TreeBuilder;
import vproxy.base.util.objectpool.ConcurrentObjectPool;
import vproxy.base.util.objectpool.CursorList;
import vproxy.base.util.objectpool.PrototypeObjectList;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        PrototypeObjectList<Integer> pool = new PrototypeObjectList<>(4, () -> ++count[0]);
        assertEquals(1, pool.add().intValue());
        assertEquals(2, pool.add().intValue());
        assertEquals(3, pool.add().intValue());
        assertEquals(3, count[0]);
        pool.clear();
        assertEquals(1, pool.add().intValue());
        assertEquals(2, pool.add().intValue());
        assertEquals(3, pool.add().intValue());
        assertEquals(3, count[0]);

        assertEquals(4, pool.add().intValue());
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

    @Test
    public void ringQueue() {
        RingQueue<Integer> q = new RingQueue<>(5);
        assertEquals("[]", q.toString());
        assertEquals(5, q.currentCapacity());

        q.add(1);
        assertEquals("[1]", q.toString());
        assertEquals(1, q.size());
        assertEquals(5, q.currentCapacity());

        q.add(2);
        assertEquals("[1, 2]", q.toString());
        assertEquals(2, q.size());
        assertEquals(5, q.currentCapacity());

        assertEquals(1, q.poll().intValue());
        assertEquals(1, q.size());
        assertEquals(5, q.currentCapacity());
        assertEquals(2, q.poll().intValue());
        assertEquals(0, q.size());
        assertEquals(5, q.currentCapacity());

        assertNull(q.poll());
        assertEquals(0, q.size());
        assertEquals(5, q.currentCapacity());

        q.add(1);
        q.add(2);
        q.add(3);
        q.add(4);
        assertEquals("[1, 2, 3, 4]", q.toString());
        assertEquals(4, q.size());
        assertEquals(5, q.currentCapacity());
        q.poll();
        assertEquals("[2, 3, 4]", q.toString());
        assertEquals(3, q.size());
        assertEquals(5, q.currentCapacity());
        q.poll();
        assertEquals("[3, 4]", q.toString());
        assertEquals(2, q.size());
        assertEquals(5, q.currentCapacity());
        q.add(5);
        assertEquals("[3, 4, 5]", q.toString());
        assertEquals(3, q.size());
        assertEquals(5, q.currentCapacity());
        q.add(6);
        assertEquals("[3, 4, 5, 6]", q.toString());
        assertEquals(4, q.size());
        assertEquals(5, q.currentCapacity());
        q.add(7);
        assertEquals("[3, 4, 5, 6, 7]", q.toString());
        assertEquals(5, q.size());
        assertEquals(5, q.currentCapacity());

        assertEquals(3, q.poll().intValue());
        assertEquals(4, q.size());
        assertEquals(5, q.currentCapacity());

        q.add(8);
        assertEquals("[4, 5, 6, 7, 8]", q.toString());
        assertEquals(5, q.size());
        assertEquals(5, q.currentCapacity());

        q.add(9);
        assertEquals("[4, 5, 6, 7, 8, 9]", q.toString());
        assertEquals(6, q.size());
        assertEquals(15, q.currentCapacity());
        q.add(10);
        assertEquals("[4, 5, 6, 7, 8, 9, 10]", q.toString());
        assertEquals(7, q.size());
        assertEquals(15, q.currentCapacity());

        assertEquals(4, q.poll().intValue());
        assertEquals(5, q.poll().intValue());
        assertEquals(6, q.poll().intValue());
        assertEquals(7, q.poll().intValue());
        assertEquals(8, q.poll().intValue());
        assertEquals(9, q.poll().intValue());
        assertEquals(10, q.poll().intValue());
        assertNull(q.poll());
        assertEquals(0, q.size());
        assertEquals(15, q.currentCapacity());

        q = new RingQueue<>(2);
        q.add(1);
        q.add(2);
        assertEquals("[1, 2]", q.toString());
        assertEquals(1, q.poll().intValue());
        assertEquals(2, q.poll().intValue());

        q.add(1);
        q.add(2);
        q.add(3);
        assertEquals("[1, 2, 3]", q.toString());
        assertEquals(12, q.currentCapacity());
    }

    @Test
    public void treeBuilder() {
        TreeBuilder tb = new TreeBuilder();
        var a = tb.branch("a");
        var b = a.branch("b");
        var c = b.branch("c");
        c.leaf("d");
        c.leaf("e");
        var f = b.branch("f");
        f.leaf("g");
        var h = a.branch("h");
        h.branch("i");
        h.leaf("j");
        assertEquals("" +
            "o\n" +
            "|\n" +
            "+---> a\n" +
            "      |\n" +
            "      +---> b\n" +
            "      |     |\n" +
            "      |     +---> c\n" +
            "      |     |     |\n" +
            "      |     |     +---> d\n" +
            "      |     |     |\n" +
            "      |     |     +---> e\n" +
            "      |     |\n" +
            "      |     +---> f\n" +
            "      |           |\n" +
            "      |           +---> g\n" +
            "      |\n" +
            "      +---> h\n" +
            "            |\n" +
            "            +---> i\n" +
            "            |\n" +
            "            +---> j\n" +
            "", tb.toString());
    }
}
