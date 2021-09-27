package io.vproxy.vfd;

import java.util.Objects;

public class RegisterEntry {
    public final FD fd;
    public final EventSet eventSet; // nullable for entries that are already invalid
    public final Object attachment;

    public RegisterEntry(FD fd, EventSet eventSet, Object attachment) {
        this.fd = fd;
        this.eventSet = eventSet;
        this.attachment = attachment;
    }

    @Override
    public String toString() {
        return "RegisterEntry{" +
            "fd=" + fd +
            ", eventSet=" + eventSet +
            ", attachment=" + attachment +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterEntry that = (RegisterEntry) o;
        return Objects.equals(fd, that.fd) &&
            Objects.equals(eventSet, that.eventSet) &&
            Objects.equals(attachment, that.attachment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fd, eventSet, attachment);
    }
}
