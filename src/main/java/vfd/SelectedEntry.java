package vfd;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SelectedEntry that = (SelectedEntry) o;
        return Objects.equals(fd, that.fd) &&
            Objects.equals(ready, that.ready) &&
            Objects.equals(attachment, that.attachment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fd, ready, attachment);
    }
}
