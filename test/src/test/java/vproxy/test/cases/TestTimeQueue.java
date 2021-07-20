package vproxy.test.cases;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import vproxy.base.util.Tuple;
import vproxy.base.util.time.TimeQueue;
import vproxy.base.util.time.impl.TimeQueueImpl;
import vproxy.base.util.time.impl.TimeWheel;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TestTimeQueue {
    private TimeQueue<String> queue;
    private long current = 0L;

    @Before
    public void setUp() throws Exception {
        queue = new TimeQueueImpl<>(current);
    }

    @After
    public void tearDown() throws Exception {
        current = 0L;
    }

    private int sleepForQueue(long duration) {
        current += duration;
        return queue.nextTime(current);
    }

    private Tuple<Integer, String> pushRandomTimeTask(int origin, int bound) {
        int timeout = ThreadLocalRandom.current().nextInt(origin, bound);
        String elem = timeout + "#" + UUID.randomUUID();
        queue.add(current, timeout, elem);
        return new Tuple<>(timeout, elem);
    }

    public void buildRandomTest(int origin, int bound, int taskNum) {
        final TreeMap<Integer, List<String>> taskMap = new TreeMap<>();
        for (int i = 0; i < taskNum; i++) {
            Tuple<Integer, String> tuple = pushRandomTimeTask(origin, bound);
            taskMap.computeIfAbsent(tuple.getKey(), k -> new ArrayList<>()).add(tuple.getValue());
        }

        for (Map.Entry<Integer, List<String>> entry : taskMap.entrySet()) {
            long duration = queue.nextTime(current);
            sleepForQueue(duration);

            List<String> strings = entry.getValue();
            for (String ignored : strings) {
                Assert.assertEquals(0, queue.nextTime(current));
                String poll = queue.poll();
                Assert.assertTrue(String.format("timestamp=%d, poll=%s", entry.getKey(), poll), strings.contains(poll));
            }
        }
        Assert.assertTrue(queue.isEmpty());
    }

    @Test
    public void firstWheel() {
        buildRandomTest(1, TimeWheel.WHEEL_SIZE, 1000);
    }

    @Test
    public void highWheel() {
        buildRandomTest(TimeWheel.WHEEL_SIZE, (int) Math.pow(TimeWheel.WHEEL_SIZE, 4), 1000);
    }

    @Test
    public void outOfWheel() {
        buildRandomTest((int) Math.pow(TimeWheel.WHEEL_SIZE, 4), (int) Math.pow(TimeWheel.WHEEL_SIZE, 5), 1000);
    }

    @Test
    public void level1() {
        String elem = UUID.randomUUID().toString();
        queue.add(current, 10, elem);
        Assert.assertNull(queue.poll());

        sleepForQueue(9);
        Assert.assertNull(queue.poll());

        sleepForQueue(1);
        Assert.assertEquals(elem, queue.poll());
        Assert.assertNull(queue.poll());
    }

    @Test
    public void sameTime() {
        String elem = UUID.randomUUID().toString();
        queue.add(current, 10, elem);
        String elem2 = UUID.randomUUID().toString();
        queue.add(current, 10, elem2);
        Assert.assertNull(queue.poll());

        sleepForQueue(9);
        Assert.assertNull(queue.poll());

        Assert.assertEquals(0, sleepForQueue(1));
        Assert.assertEquals(elem, queue.poll());

        Assert.assertEquals(0, queue.nextTime(current));
        Assert.assertEquals(elem2, queue.poll());
        Assert.assertNull(queue.poll());
    }
}
