package vfd;

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
}
