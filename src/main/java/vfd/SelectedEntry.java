package vfd;

public class SelectedEntry {
    public final FD fd;
    public final EventSet ready;
    public final Object attachment;

    public SelectedEntry(FD fd, EventSet ready, Object attachment) {
        this.fd = fd;
        this.ready = ready;
        this.attachment = attachment;
    }

    @Override
    public String toString() {
        return "SelectedEntry{" +
            "fd=" + fd +
            ", ready=" + ready +
            ", attachment=" + attachment +
            '}';
    }
}
