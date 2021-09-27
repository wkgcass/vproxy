package io.vproxy.vfd.posix;

public class FDInfo {
    private int fd;
    private int events;
    private Object attachment;

    public FDInfo set(int fd, int events, Object attachment) {
        this.fd = fd;
        this.events = events;
        this.attachment = attachment;
        return this;
    }

    public int fd() {
        return fd;
    }

    public int events() {
        return events;
    }

    public Object attachment() {
        return attachment;
    }

    @Override
    public String toString() {
        return "FDInfo{" +
            "fd=" + fd +
            ", events=" + events +
            ", attachment=" + attachment +
            '}';
    }
}
