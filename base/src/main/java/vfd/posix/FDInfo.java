package vfd.posix;

public class FDInfo {
    public final int fd;
    public final int events;
    public final Object attachment;

    public FDInfo(int fd, int events, Object attachment) {
        this.fd = fd;
        this.events = events;
        this.attachment = attachment;
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
