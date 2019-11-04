package vfd;

public class RegisterEntry {
    public final FD fd;
    public final EventSet eventSet;
    public final Object attachment;

    public RegisterEntry(FD fd, EventSet eventSet, Object attachment) {
        this.fd = fd;
        this.eventSet = eventSet;
        this.attachment = attachment;
    }
}
