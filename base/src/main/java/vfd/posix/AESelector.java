package vfd.posix;

import vfd.*;
import vproxybase.util.DirectMemoryUtils;
import vproxybase.util.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.*;

public class AESelector implements FDSelector {
    private final Posix posix;
    private final long ae;
    private final int[] pipefd; // null, or pipefd[read][write], might be the same if using linux eventfd
    private final ByteBuffer bufferForPipeFD;
    private boolean closed = false;

    private final int aeReadable;
    private final int aeWritable;
    private final boolean onlySelectNow;

    public AESelector(Posix posix, long ae, int[] pipefd) {
        this.posix = posix;
        this.ae = ae;
        this.aeReadable = posix.aeReadable();
        this.aeWritable = posix.aeWritable();
        this.pipefd = pipefd;
        if (pipefd == null) {
            bufferForPipeFD = null;
        } else {
            bufferForPipeFD = DirectMemoryUtils.allocateDirectBuffer(8); // linux eventfd requires 8 bytes buffer
            posix.aeCreateFileEvent(ae, pipefd[0], this.aeReadable, new Att(null, null));
        }
        onlySelectNow = posix.onlySelectNow();
    }

    private static class Att {
        final FD fd;

        final Object att;

        private Att(FD fd, Object att) {
            this.fd = fd;
            this.att = att;
        }

        @Override
        public String toString() {
            return "Att{" +
                "fd=" + fd +
                ", att=" + att +
                '}';
        }
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    private EventSet getJavaEvents(int events) {
        EventSet ret = EventSet.none();
        if ((events & aeReadable) == aeReadable) {
            ret = ret.combine(EventSet.read());
        }
        if ((events & aeWritable) == aeWritable) {
            ret = ret.combine(EventSet.write());
        }
        return ret;
    }

    private void clearPipeFD() {
        if (pipefd != null) {
            while (true) {
                int x;
                try {
                    x = posix.read(pipefd[0], bufferForPipeFD, 0, 8);
                } catch (IOException e) {
                    Logger.shouldNotHappen("reading from read end of pipefd failed", e);
                    break;
                }
                assert x == 0 || x == 8;
                if (x == 0) {
                    break;
                }
            }
        }
    }

    private Collection<SelectedEntry> handleSelectResult(FDInfo[] results) {
        clearPipeFD();
        if (results.length == 0) {
            return Collections.emptyList();
        }
        List<SelectedEntry> ret = new ArrayList<>(results.length);
        for (FDInfo res : results) {
            Att att = (Att) res.attachment;
            if (att.fd == null) // for the internal pipe fds
                continue;
            int ev = res.events;
            ret.add(new SelectedEntry(att.fd, getJavaEvents(ev), att.att));
        }
        return ret;
    }

    private void checkOpen() {
        if (closed) {
            throw new ClosedSelectorException();
        }
    }

    @Override
    public Collection<SelectedEntry> select() throws IOException {
        if (onlySelectNow) {
            throw new UnsupportedOperationException("only selectNow supported");
        }
        checkOpen();
        var res = posix.aeApiPoll(ae, 24 * 60 * 60 * 1000);
        return handleSelectResult(res);
    }

    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        checkOpen();
        var res = posix.aeApiPoll(ae, 0);
        return handleSelectResult(res);
    }

    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        if (onlySelectNow) {
            throw new UnsupportedOperationException("only selectNow supported");
        }
        checkOpen();
        var res = posix.aeApiPoll(ae, millis);
        return handleSelectResult(res);
    }

    @Override
    public boolean supportsWakeup() {
        return pipefd != null;
    }

    @Override
    public void wakeup() {
        if (pipefd == null) {
            throw new UnsupportedOperationException("does not support wakeup");
        }
        checkOpen();
        bufferForPipeFD.limit(8).position(0).putLong(1L);
        try {
            posix.write(pipefd[1], bufferForPipeFD, 0, 8);
        } catch (IOException e) {
            Logger.shouldNotHappen("writing to write end of pipefd[1] failed", e);
        }
    }

    @Override
    public boolean isRegistered(FD fd) {
        checkOpen();
        return null != posix.aeGetClientData(ae, ((PosixFD) fd.real()).fd);
    }

    private int getIntEvents(EventSet events) {
        int ret = 0;
        if (events.have(Event.READABLE)) {
            ret |= aeReadable;
        }
        if (events.have(Event.WRITABLE)) {
            ret |= aeWritable;
        }
        return ret;
    }

    @Override
    public void register(FD fd, EventSet ops, Object registerData) throws ClosedChannelException {
        checkOpen();
        if (!fd.isOpen()) {
            throw new ClosedChannelException();
        }
        posix.aeCreateFileEvent(ae, ((PosixFD) fd.real()).fd, getIntEvents(ops), new Att(fd, registerData));
    }

    @Override
    public void remove(FD fd) {
        checkOpen();
        posix.aeDeleteFileEvent(ae, ((PosixFD) fd.real()).fd);
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        checkOpen();
        posix.aeUpdateFileEvent(ae, ((PosixFD) fd.real()).fd, getIntEvents(ops));
    }

    @Override
    public EventSet events(FD fd) {
        checkOpen();
        return getJavaEvents(posix.aeGetFileEvents(ae, ((PosixFD) fd.real()).fd));
    }

    @Override
    public Object attachment(FD fd) {
        checkOpen();
        return ((Att) posix.aeGetClientData(ae, ((PosixFD) fd.real()).fd)).att;
    }

    @Override
    public Collection<RegisterEntry> entries() {
        checkOpen();
        FDInfo[] fds = posix.aeAllFDs(ae);
        List<RegisterEntry> ret = new ArrayList<>(fds.length);
        for (FDInfo fd : fds) {
            var att = (Att) fd.attachment;
            if (att.fd == null) // for the internal pipe fds
                continue;
            ret.add(new RegisterEntry(att.fd, getJavaEvents(fd.events), att.att));
        }
        return ret;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        posix.aeDeleteEventLoop(ae);
        if (bufferForPipeFD != null) {
            DirectMemoryUtils.free(bufferForPipeFD);
        }
        if (pipefd != null) {
            try {
                posix.close(pipefd[0]);
            } catch (IOException e) {
                Logger.shouldNotHappen("closing read end of the pipefd failed", e);
            }
            if (pipefd[1] != pipefd[0]) {
                try {
                    posix.close(pipefd[1]);
                } catch (IOException e) {
                    Logger.shouldNotHappen("closing write end of the pipefd failed", e);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() {
        close();
    }

    @Override
    public String toString() {
        return "AESelector{" +
            "ae=" + ae +
            ", pipefd=" + Arrays.toString(pipefd) +
            ", closed=" + closed +
            '}';
    }
}
