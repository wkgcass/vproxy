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

    public void add(FD fd, EventSet ready, Object attachment) {
        add().set(fd, ready, attachment);
    }
}
