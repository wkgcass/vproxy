package io.vproxy.vfd.posix;

import io.vproxy.base.util.objectpool.PrototypeObjectList;
import io.vproxy.vfd.EventSet;
import io.vproxy.vfd.FD;
import io.vproxy.vfd.SelectedEntry;

import java.util.function.Supplier;

public class SelectedEntryPrototypeObjectList extends PrototypeObjectList<SelectedEntry> {
    public SelectedEntryPrototypeObjectList(int capacity, Supplier<SelectedEntry> constructor) {
        super(capacity, constructor);
    }

    // Keep reusable prototypes, but release the previous batch's payloads.
    // Only the last logical batch is visited, never historical capacity.
    @Override
    public void clear() {
        int count = size();
        for (int i = 0; i < count; ++i) {
            get(i).set(null, null, null);
        }
        super.clear();
    }

    public void add(FD fd, EventSet ready, Object attachment) {
        add().set(fd, ready, attachment);
    }
}
