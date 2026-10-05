package io.vproxy.test.cases;

import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.selector.wrap.WritableAware;
import io.vproxy.base.selector.wrap.WrappedSelector;
import io.vproxy.vfd.*;
import io.vproxy.vfd.jdk.ChannelFDs;
import io.vproxy.vfd.posix.SelectedEntryPrototypeObjectList;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.util.*;

import static org.junit.Assert.*;

public class TestSelectorResultLifecycle {
    private static class Input implements VirtualFD {
        public boolean isOpen() { return true; }
        public void close() { }
        public void configureBlocking(boolean b) { }
        public <T> void setOption(SocketOption<T> name, T value) { }
        public FD real() { return this; }
        public boolean contains(FD fd) { return fd == this; }
        public void onRegister() { }
        public void onRemove() { }
    }
    private static class Hybrid implements FD {
        final Input input;
        Hybrid(Input input) { this.input = input; }
        public boolean isOpen() { return true; }
        public void close() { }
        public void configureBlocking(boolean b) { }
        public <T> void setOption(SocketOption<T> name, T value) { }
        public FD real() { return input; }
        public boolean contains(FD fd) { return fd == this || fd == input; }
    }
    private static class EmptySelector implements FDSelector {
        boolean open = true;
        Runnable beforeSelect = () -> { };
        IOException failure;
        Collection<SelectedEntry> results = List.of();
        public boolean isOpen() { return open; }
        public Collection<SelectedEntry> select() throws IOException { beforeSelect.run(); if (failure != null) throw failure; return results; }
        public Collection<SelectedEntry> selectNow() throws IOException { return select(); }
        public Collection<SelectedEntry> select(long millis) throws IOException { return select(); }
        public void wakeup() { }
        public boolean isRegistered(FD fd) { return false; }
        public void register(FD fd, EventSet e, Object att) { throw new AssertionError(); }
        public void remove(FD fd) { throw new AssertionError(); }
        public void modify(FD fd, EventSet e) { throw new AssertionError(); }
        public EventSet events(FD fd) { throw new AssertionError(); }
        public Object attachment(FD fd) { throw new AssertionError(); }
        public Collection<RegisterEntry> entries() { return List.of(); }
        public void close() { open = false; }
    }
    private static Collection<SelectedEntry> poll(FDSelector selector, int mode) throws IOException {
        return switch (mode) { case 0 -> selector.select(); case 1 -> selector.selectNow(); default -> selector.select(1); };
    }
    private static void cleared(SelectedEntry entry) {
        assertNull("previous batch still retains FD", entry.fd());
        assertNull("previous batch still retains attachment", entry.attachment());
        assertNull(entry.ready());
    }
    @Test
    public void poolClearReusesEntriesAndDoesNotRevisitIdleCapacity() {
        int[] resets = {0};
        var pool = new SelectedEntryPrototypeObjectList(1, () -> new SelectedEntry() {
            @Override public SelectedEntry set(FD fd, EventSet events, Object attachment) {
                if (fd == null && events == null && attachment == null) ++resets[0];
                return super.set(fd, events, attachment);
            }
        });
        SelectedEntry[] entries = new SelectedEntry[1000];
        for (int i = 0; i < entries.length; ++i) entries[i] = pool.add().set(new Input(), EventSet.read(), new Object());
        pool.clear();
        for (var entry : entries) cleared(entry);
        assertEquals(1000, resets[0]);
        for (int i = 0; i < 100; ++i) pool.clear();
        assertEquals("idle clear must not scan the historical peak", 1000, resets[0]);
        assertSame(entries[0], pool.add());
        pool.get(0).set(new Input(), EventSet.write(), new Object());
        pool.clear();
        assertEquals(1001, resets[0]);
        assertEquals(1000, pool.total());
    }
    @Test
    public void removedVirtualAndHybridBatchesReleaseBeforeNextWait() throws Exception {
        for (boolean hybrid : new boolean[]{false, true}) for (int mode = 0; mode < 3; ++mode) {
            var underlying = new EmptySelector();
            try (var selector = new WrappedSelector(underlying)) {
                FD[] registered = new FD[1000];
                for (int i = 0; i < registered.length; ++i) {
                    var input = new Input(); registered[i] = hybrid ? new Hybrid(input) : input;
                    selector.register(registered[i], EventSet.read(), new Object()); selector.registerVirtualReadable(input);
                }
                SelectedEntry[] entries = selector.selectNow().toArray(SelectedEntry[]::new);
                assertEquals(1000, entries.length);
                for (var fd : registered) selector.remove(fd);
                underlying.beforeSelect = () -> { for (var entry : entries) cleared(entry); };
                assertTrue(poll(selector, mode).isEmpty());
                for (int i = 0; i < 100; ++i) assertTrue(selector.selectNow().isEmpty());
            }
        }
    }
    @Test
    public void smallerBatchReleasesItsUnusedTail() throws Exception {
        try (var selector = new WrappedSelector(new EmptySelector())) {
            List<Input> inputs = new ArrayList<>();
            for (int i = 0; i < 100; ++i) { var input = new Input(); inputs.add(input); selector.register(input, EventSet.read(), input); selector.registerVirtualReadable(input); }
            SelectedEntry[] old = selector.selectNow().toArray(SelectedEntry[]::new);
            var active = inputs.get(0);
            for (int i = 1; i < inputs.size(); ++i) selector.remove(inputs.get(i));
            var selected = selector.selectNow(); assertEquals(1, selected.size());
            var entry = selected.iterator().next(); assertSame(active, entry.fd()); assertSame(active, entry.attachment());
            for (var previous : old) if (previous != entry) cleared(previous);
        }
    }
    @Test
    public void removingOrClosingDoesNotInvalidateTheCurrentBatch() throws Exception {
        var selector = new WrappedSelector(new EmptySelector());
        try {
            var first = new Input(); var second = new Input();
            for (var fd : List.of(first, second)) { selector.register(fd, EventSet.readwrite(), fd); selector.registerVirtualReadable(fd); selector.registerVirtualWritable(fd); }
            var selected = selector.selectNow().toArray(SelectedEntry[]::new);
            selector.remove(first); selector.close();
            assertEquals(2, selected.length);
            for (var entry : selected) { assertNotNull(entry.fd()); assertSame(entry.fd(), entry.attachment()); assertEquals(EventSet.readwrite(), entry.ready()); }
        } finally { selector.close(); }
    }
    @Test
    public void selectionFailureStillReleasesThePreviousBatch() throws Exception {
        var underlying = new EmptySelector();
        try (var selector = new WrappedSelector(underlying)) {
            var fd = new Input(); selector.register(fd, EventSet.read(), new Object()); selector.registerVirtualReadable(fd);
            var old = selector.selectNow().iterator().next(); selector.remove(fd);
            var failure = new IOException("controlled poll failure"); underlying.failure = failure;
            try { selector.select(); fail("expected IOException"); } catch (IOException e) { assertSame(failure, e); }
            cleared(old);
        }
    }
    @Test
    public void jdkUdpBatchesReleaseAfterRemovalInEverySelectMode() throws Exception {
        var fds = ChannelFDs.get();
        for (int mode = 0; mode < 3; ++mode) try (var selector = fds.openSelector(); var receiver = fds.openDatagramFD(); var sender = fds.openDatagramFD()) {
            receiver.configureBlocking(false); sender.configureBlocking(false); receiver.bind(new IPPort("127.0.0.1", 0));
            var marker = new Object(); selector.register(receiver, EventSet.read(), marker);
            assertEquals(1, sender.send(ByteBuffer.wrap(new byte[]{42}), receiver.getLocalAddress()));
            SelectedEntry selected = null; long deadline = System.nanoTime() + 3_000_000_000L;
            while (selected == null && System.nanoTime() < deadline) for (var entry : selector.selectNow()) {
                assertSame(receiver, entry.fd()); assertSame(marker, entry.attachment()); selected = entry;
            }
            assertNotNull("real UDP selection", selected);
            var b = ByteBuffer.allocate(8); assertNotNull(receiver.receive(b)); assertEquals(1, b.position()); assertEquals(42, b.get(0));
            selector.remove(receiver); selector.wakeup(); assertTrue(poll(selector, mode).isEmpty());
            cleared(selected);
        }
    }
    private static class Aware extends Input implements WritableAware {
        Runnable action = () -> { };
        public void writable() { action.run(); }
    }
    @Test
    public void unusedTailIsReleasedBeforeWritableCallbacksEvenOnFailure() throws Exception {
        for (boolean failCallback : new boolean[]{false, true}) try (var selector = new WrappedSelector(new EmptySelector())) {
            var active = new Aware();
            List<Input> removed = new ArrayList<>();
            selector.register(active, EventSet.readwrite(), active); selector.registerVirtualWritable(active);
            for (int i = 0; i < 100; ++i) {
                var fd = new Input(); removed.add(fd); selector.register(fd, EventSet.read(), fd); selector.registerVirtualReadable(fd);
            }
            var previous = selector.selectNow().toArray(SelectedEntry[]::new);
            for (var fd : removed) selector.remove(fd);
            var error = new IllegalStateException("controlled writable callback failure");
            int[] calls = {0};
            active.action = () -> {
                ++calls[0];
                // The first prototype is reused for the only current entry.
                assertSame(active, previous[0].fd());
                for (int i = 1; i < previous.length; ++i) cleared(previous[i]);
                if (failCallback) throw error;
            };
            if (failCallback) {
                try { selector.selectNow(); fail("expected callback error"); } catch (IllegalStateException e) { assertSame(error, e); }
            } else {
                var selected = selector.selectNow(); assertEquals(1, selected.size()); assertSame(active, selected.iterator().next().fd());
            }
            assertEquals(1, calls[0]);
        }
    }
    @Test
    public void collectingFailureStillReleasesUnusedTail() throws Exception {
        class Fallible extends Input {
            boolean failHash;
            @Override public int hashCode() { if (failHash) throw new IllegalStateException("controlled collection failure"); return 12345; }
        }
        try (var selector = new WrappedSelector(new EmptySelector())) {
            var fd = new Fallible(); selector.register(fd, EventSet.read(), new Object()); selector.registerVirtualReadable(fd);
            var previous = selector.selectNow().iterator().next(); fd.failHash = true;
            try { selector.selectNow(); fail("expected collection error"); } catch (IllegalStateException e) { assertEquals("controlled collection failure", e.getMessage()); }
            cleared(previous);
            fd.failHash = false; assertEquals(1, selector.selectNow().size());
        }
    }
    @Test
    public void realSelectionCanRefillClearedTailWithoutChangingVirtualBatch() throws Exception {
        var underlying = new EmptySelector();
        try (var selector = new WrappedSelector(underlying)) {
            var active = new Input(); selector.register(active, EventSet.read(), active); selector.registerVirtualReadable(active);
            List<Input> removed = new ArrayList<>();
            for (int i = 0; i < 50; ++i) { var fd = new Input(); removed.add(fd); selector.register(fd, EventSet.read(), fd); selector.registerVirtualReadable(fd); }
            var previous = selector.selectNow().toArray(SelectedEntry[]::new);
            for (var fd : removed) selector.remove(fd);
            var real = new Hybrid(new Input()); var marker = new Object();
            underlying.beforeSelect = () -> { for (int i = 1; i < previous.length; ++i) cleared(previous[i]); };
            underlying.results = List.of(new SelectedEntry().set(real, EventSet.write(), marker));
            var selected = selector.selectNow().toArray(SelectedEntry[]::new); assertEquals(2, selected.length);
            assertSame(active, selected[0].fd()); assertSame(active, selected[0].attachment()); assertEquals(EventSet.read(), selected[0].ready());
            assertSame(real, selected[1].fd()); assertSame(marker, selected[1].attachment()); assertEquals(EventSet.write(), selected[1].ready());
            for (int i = 2; i < previous.length; ++i) cleared(previous[i]);
        }
    }

}
