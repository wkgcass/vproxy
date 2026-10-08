package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.HandlerContext;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.vfd.*;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class TestEventLoopDiagnostics {
    static class Access {
        final Class<?> type = Class.forName("io.vproxy.base.selector.EventLoopDiagnostics");
        final Object value;
        final Method record, report;
        Access() throws Exception {
            var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true); value = ctor.newInstance();
            record = type.getDeclaredMethod("record", int.class, Object.class, long.class); record.setAccessible(true);
            report = type.getDeclaredMethod("report", long.class); report.setAccessible(true);
        }
        void record(int kind, long ns) throws Exception { record.invoke(value, kind, this, ns); }
        Object metric(int kind) throws Exception { var f=type.getDeclaredField("metrics");f.setAccessible(true);return ((Object[])f.get(value))[kind]; }
        long field(Object o, String name) throws Exception { var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return ((Number)f.get(o)).longValue(); }
        long quantile(int kind, int p) throws Exception { var o=metric(kind);var m=o.getClass().getDeclaredMethod("percentileUpperBound",int.class);m.setAccessible(true);return (Long)m.invoke(o,p); }
        void attach(SelectorEventLoop loop) throws Exception { var f=SelectorEventLoop.class.getDeclaredField("diagnostics");f.setAccessible(true);f.set(loop,value); }
    }

    @Test public void durationDistributionSeparatesWaitFromCallbacks() throws Exception {
        var a=new Access();
        for(int i=0;i<98;++i)a.record(2,1000);
        a.record(2,2000);a.record(2,1_000_000);
        a.record(6,30_000_000_000L);
        assertEquals(100,a.field(a.metric(2),"count"));
        assertEquals(1_100_000,a.field(a.metric(2),"nanos"));
        assertEquals(1000,a.quantile(2,95));assertEquals(2000,a.quantile(2,99));
        assertEquals(1_024_000,a.quantile(2,100));
        assertEquals(30_000_000_000L,a.field(a.metric(6),"maxNanos"));
    }

    @Test public void reportsAndStorageAreBoundedAcrossClockWrap() throws Exception {
        var a=new Access();long now=Long.MAX_VALUE-1_000_000_000L;
        a.report.invoke(a.value,now);assertEquals(0,a.field(a.value,"reportCount"));
        a.record(2,10);a.report.invoke(a.value,now);assertEquals(1,a.field(a.value,"reportCount"));
        a.report.invoke(a.value,now+1);assertEquals(1,a.field(a.value,"reportCount"));
        for(int i=0;i<100;++i){now+=15_000_000_000L;a.report.invoke(a.value,now);}
        assertEquals(32,a.field(a.value,"reportCount"));
        for(int kind=0;kind<9;++kind){var o=a.metric(kind);var f=o.getClass().getDeclaredField("histogram");f.setAccessible(true);assertEquals(32,((long[])f.get(o)).length);}
    }

    @Test public void realLoopAccountsForTasksTimersAndTunHandlers() throws Exception {
        var a=new Access();
        try(var f=new TestTunReadFairness.Fixture(1,2_000_000)){
            a.attach(f.loop);
            f.loop.nextTick(()->f.clock.nanos+=3_000_000);
            f.loop.delay(0,()->f.clock.nanos+=5_000_000);
            f.loop.onePoll();
            assertEquals(2_000_000,a.field(a.metric(2),"nanos"));
            assertEquals(1,a.field(a.metric(2),"count"));
            assertEquals(3_000_000,a.field(a.metric(0),"nanos"));
            assertEquals(5_000_000,a.field(a.metric(1),"nanos"));
            assertEquals(1,a.field(a.metric(6),"count"));
        }
    }

    @Test public void callbackExceptionsStillCountAndNextCallbackRuns() throws Exception {
        var a=new Access();
        try(var f=new TestTunReadFairness.Fixture(0,0)){
            a.attach(f.loop);
            f.loop.nextTick(()->{f.clock.nanos+=7_000_000;throw new IllegalStateException("diagnostic test");});
            f.loop.nextTick(()->f.clock.nanos+=3_000_000);
            f.loop.onePoll();
            assertEquals(2,a.field(a.metric(0),"count"));
            assertEquals(10_000_000,a.field(a.metric(0),"nanos"));
            assertEquals(7_000_000,a.field(a.metric(0),"maxNanos"));
        }
    }
    static abstract class TestFD implements VirtualFD {
        public boolean isOpen() { return true; }
        public void close() { }
        public void configureBlocking(boolean b) { }
        public <T> void setOption(SocketOption<T> key, T value) { }
        public FD real() { return this; }
        public boolean contains(FD fd) { return fd == this; }
        public void onRegister() { }
        public void onRemove() { }
    }

    static class TestSocket extends TestFD implements SocketFD {
        public void connect(IPPort addr) { }
        public boolean isConnected() { return false; }
        public boolean finishConnect() { return true; }
        public void shutdownOutput() { }
        public IPPort getLocalAddress() { return null; }
        public IPPort getRemoteAddress() { return null; }
        public int read(ByteBuffer b) { return 0; }
        public int write(ByteBuffer b) { return 0; }
    }

    static class TestServer extends TestFD implements ServerSocketFD {
        public IPPort getLocalAddress() { return null; }
        public SocketFD accept() { return null; }
        public void bind(IPPort addr) { }
    }

    @Test public void realLoopMeasuresConnectWriteAcceptAndPollHooks() throws Exception {
        var a = new Access();
        try (var f = new TestTunReadFairness.Fixture(0, 0)) {
            a.attach(f.loop);
            f.loop.setBeforePoll(() -> { f.clock.nanos += 11_000_000; return 0; });
            f.loop.setAfterPoll((n, arr) -> { f.clock.nanos += 13_000_000; return false; });
            Handler<FD> handler = new Handler<>() {
                public void accept(HandlerContext<FD> ctx) { f.clock.nanos += 5_000_000; }
                public void connected(HandlerContext<FD> ctx) { f.clock.nanos += 3_000_000; }
                public void writable(HandlerContext<FD> ctx) { f.clock.nanos += 7_000_000; }
                public void readable(HandlerContext<FD> ctx) { f.clock.nanos += 2_000_000; throw new IllegalStateException("read test"); }
                public void removed(HandlerContext<FD> ctx) { }
            };
            var socket = new TestSocket();
            var server = new TestServer();
            f.loop.add(socket, EventSet.readwrite(), null, handler);
            f.loop.add(server, EventSet.read(), null, handler);
            f.loop.selector.registerVirtualReadable(socket);
            f.loop.selector.registerVirtualWritable(socket);
            f.loop.selector.registerVirtualReadable(server);
            f.loop.onePoll();
            f.loop.onePoll();
            assertEquals(2, a.field(a.metric(2), "count"));
            assertEquals(4_000_000, a.field(a.metric(2), "nanos"));
            assertEquals(1, a.field(a.metric(5), "count"));
            assertEquals(3_000_000, a.field(a.metric(5), "nanos"));
            assertEquals(1, a.field(a.metric(3), "count"));
            assertEquals(7_000_000, a.field(a.metric(3), "nanos"));
            assertEquals(2, a.field(a.metric(4), "count"));
            assertEquals(10_000_000, a.field(a.metric(4), "nanos"));
            assertEquals(22_000_000, a.field(a.metric(7), "nanos"));
            assertEquals(26_000_000, a.field(a.metric(8), "nanos"));
        }
    }

    @Test public void disabledDiagnosticsDoNotReadClockOrAllocateStats() throws Exception {
        class Clock extends TestTunReadFairness.ClockFDs {
            int calls;
            public long nanoTime() { ++calls; return super.nanoTime(); }
        }
        long previous = Config.currentTimestamp;
        var clock = new Clock();
        try (var loop = SelectorEventLoop.open(clock)) {
            loop.setBeforePoll(() -> 0);
            loop.nextTick(() -> clock.nanos += 1000);
            loop.delay(0, () -> clock.nanos += 1000);
            var field = SelectorEventLoop.class.getDeclaredField("diagnostics");
            field.setAccessible(true);
            assertNull("default off: no histogram instance", field.get(loop));
            loop.onePoll();
            assertEquals("no diagnostic nanoTime calls", 0, clock.calls);
        } finally {
            Config.currentTimestamp = previous;
        }
    }

}
