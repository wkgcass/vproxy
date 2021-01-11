package vfd.posix;

import vproxybase.util.objectpool.PrototypeObjectList;

import java.util.function.Supplier;

public class FDInfoPrototypeObjectList extends PrototypeObjectList<FDInfo> {
    public FDInfoPrototypeObjectList(int capacity, Supplier<FDInfo> constructor) {
        super(capacity, constructor);
    }

    public void add(int fd, int events, Object attachment) {
        add().set(fd, events, attachment);
    }
}
