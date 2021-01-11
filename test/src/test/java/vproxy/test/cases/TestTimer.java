package vproxy.test.cases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vproxybase.selector.PeriodicEvent;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.TimerEvent;
import vproxybase.util.thread.VProxyThread;

import static org.junit.Assert.*;

public class TestTimer {
    private SelectorEventLoop loop;

    @Before
    public void setUp() throws Exception {
        loop = SelectorEventLoop.open();
        loop.loop(r -> VProxyThread.create(r, "EventLoop"));
    }

    @After
    public void tearDown() throws Exception {
        loop.close();
    }

    @Test
    public void waitForFewSeconds() throws Exception {
        int timeout = 1000;
        boolean[] done = {false};
        loop.delay(timeout, () -> done[0] = true);
        Thread.sleep(timeout + 100);
        assertTrue("delay for 1 sec execute", done[0]);
    }

    @Test
    public void multipleTasksAtTheSameTime() throws Exception {
        boolean[] done = {false, false, false};
        loop.delay(1000, () -> done[0] = true);
        loop.delay(500, () -> done[1] = true);
        loop.delay(1500, () -> done[2] = true);
        Thread.sleep(550);
        assertArrayEquals("500 ms", new boolean[]{false, true, false}, done);
        Thread.sleep(500);
        assertArrayEquals("1000 ms", new boolean[]{true, true, false}, done);
        Thread.sleep(500);
        assertArrayEquals("1500 ms", new boolean[]{true, true, true}, done);
    }

    @Test
    public void cancel() throws Exception {
        boolean[] done = {false, false, false};
        TimerEvent te0 = loop.delay(1000, () -> done[0] = true);
        loop.delay(500, () -> done[1] = true);
        loop.delay(1500, () -> done[2] = true);
        Thread.sleep(550);
        assertArrayEquals("500 ms", new boolean[]{false, true, false}, done);
        te0.cancel(); // cancel here
        Thread.sleep(500);
        assertArrayEquals("1000 ms", new boolean[]{false/*it's canceled*/, true, false}, done);
        Thread.sleep(500);
        assertArrayEquals("1500 ms", new boolean[]{false, true, true}, done);
    }

    @Test
    public void periodic() throws Exception {
        int[] i = {0};
        PeriodicEvent e = loop.period(500, () -> ++i[0]);
        Thread.sleep(550);
        assertEquals("first alert", 1, i[0]);
        Thread.sleep(500);
        assertEquals("second alert", 2, i[0]);
        Thread.sleep(500);
        assertEquals("third alert", 3, i[0]);
        e.cancel();
        Thread.sleep(500);
        assertEquals("still 3 alerts", 3, i[0]);
    }
}
