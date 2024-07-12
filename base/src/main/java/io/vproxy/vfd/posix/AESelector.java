package io.vproxy.vfd.posix;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.direct.DirectByteBuffer;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import io.vproxy.base.util.objectpool.GarbageFree;
import io.vproxy.vfd.*;

import java.io.IOException;
import java.lang.foreign.ValueLayout;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.*;

public class AESelector implements FDSelector {
    private final SelectedEntryPrototypeObjectList selectedEntryList = new SelectedEntryPrototypeObjectList(128, SelectedEntry::new);
    private final FDInfoPrototypeObjectList fdInfoList = new FDInfoPrototypeObjectList(128, FDInfo::new);

    private final Posix posix;
    private final long ae;
    private final int[] pipefd; // null, or pipefd[read][write], might be the same if using linux eventfd
    private final Att[] attachments;
    private final DirectByteBuffer bufferForPipeFD;
    private boolean closed = false;

    private final int aeReadable;
    private final int aeWritable;

    private final AEFiredEvent.Array fired;
    private final AEFiredExtra.Array firedExtra;

    public AESelector(Posix posix, long ae, int[] pipefd, int setsize) {
        this.posix = posix;
        this.ae = ae;
        this.aeReadable = posix.aeReadable();
        this.aeWritable = posix.aeWritable();
        this.pipefd = pipefd;
        bufferForPipeFD = DirectMemoryUtils.allocateDirectBuffer(8); // linux eventfd requires 8 bytes buffer
        posix.aeCreateFileEvent(ae, pipefd[0], this.aeReadable);
        attachments = new Att[setsize];
        for (int i = 0; i < setsize; ++i) {
            attachments[i] = new Att();
        }
        fired = new AEFiredEvent.Array(posix.aeGetFired(ae).reinterpret(setsize * AEFiredEvent.LAYOUT.byteSize()));
        firedExtra = new AEFiredExtra.Array(posix.aeGetFiredExtra(ae).reinterpret(setsize * AEFiredExtra.LAYOUT.byteSize()));
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
                    x = posix.read(pipefd[0], bufferForPipeFD.realBuffer(), 0, 8);
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

    private Collection<SelectedEntry> handleSelectResult() {
        if (fdInfoList.isEmpty()) {
            return Collections.emptyList();
        }
        clearPipeFD();
        selectedEntryList.clear();

        for (FDInfo res : fdInfoList) {
            Att att = (Att) res.attachment();
            if (att.fd == null) // for the internal pipe fds
                continue;
            int ev = res.events();
            selectedEntryList.add(att.fd, getJavaEvents(ev), att.att);
        }
        return selectedEntryList;
    }

    private void checkOpen() {
        if (closed) {
            throw new ClosedSelectorException();
        }
    }

    private void fillFDsList(int n) {
        for (int i = 0; i < n; ++i) {
            int fd = fired.get(i).getFd();
            int evt = fired.get(i).getMask();
            fdInfoList.add(fd, evt, attachments[fd]);
        }
    }

    @GarbageFree
    @Override
    public Collection<SelectedEntry> select() throws IOException {
        checkOpen();
        fdInfoList.clear();
        int n = posix.aeApiPoll(ae, 24 * 60 * 60 * 1000);
        fillFDsList(n);
        return handleSelectResult();
    }

    @GarbageFree
    @Override
    public Collection<SelectedEntry> selectNow() throws IOException {
        checkOpen();
        fdInfoList.clear();
        int n = posix.aeApiPollNow(ae);
        fillFDsList(n);
        return handleSelectResult();
    }

    @GarbageFree
    @Override
    public Collection<SelectedEntry> select(long millis) throws IOException {
        checkOpen();
        fdInfoList.clear();
        int n;
        if (millis <= 0) {
            n = posix.aeApiPollNow(ae);
        } else {
            n = posix.aeApiPoll(ae, millis);
        }
        fillFDsList(n);
        return handleSelectResult();
    }

    @Override
    public void wakeup() {
        if (pipefd == null) {
            throw new UnsupportedOperationException("does not support wakeup");
        }
        checkOpen();
        bufferForPipeFD.getMemorySegment().set(ValueLayout.JAVA_LONG, 0, 1L);
        try {
            posix.write(pipefd[1], bufferForPipeFD.realBuffer(), 0, 8);
        } catch (IOException e) {
            Logger.shouldNotHappen("writing to write end of pipefd[1] failed", e);
        }
    }

    private void checkFDMatch(FD fd, boolean allowNotExist) {
        var real = (PosixFD) fd.real();
        int fdnum = real.fd;
        if (attachments[fdnum].fd == null) {
            if (allowNotExist)
                return;
        }
        if (attachments[fdnum].fd != fd) {
            throw new IllegalArgumentException("fd mismatch: " +
                "input: " + fd + ", existing: " + attachments[fdnum].fd);
        }
    }

    @Override
    public boolean isRegistered(FD fd) {
        checkOpen();
        var real = (PosixFD) fd.real();
        int fdnum = real.fd;
        var stored = attachments[fdnum].fd;
        if (stored == null) {
            return false;
        }
        return stored == fd;
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
        var real = (PosixFD) fd.real();
        int fdnum = real.fd;
        if (attachments[fdnum].fd != null) {
            throw new IllegalArgumentException("trying to overwrite an existing fd in aeSelector: " +
                "input: " + fd + ", existing: " + attachments[fdnum].fd);
        }
        attachments[fdnum].set(fd, ops, registerData);
        posix.aeCreateFileEvent(ae, fdnum, getIntEvents(ops));
    }

    @Override
    public void remove(FD fd) {
        checkOpen();
        checkFDMatch(fd, true);

        var real = (PosixFD) fd.real();
        int fdnum = real.fd;
        attachments[fdnum].set(null, null, null);
        posix.aeDeleteFileEvent(ae, fdnum);
    }

    @Override
    public void modify(FD fd, EventSet ops) {
        checkOpen();
        checkFDMatch(fd, false);

        var real = (PosixFD) fd.real();
        int fdnum = real.fd;
        attachments[fdnum].ops = ops;
        posix.aeUpdateFileEvent(ae, fdnum, getIntEvents(ops));
    }

    @Override
    public EventSet events(FD fd) {
        checkOpen();
        checkFDMatch(fd, false);
        var real = (PosixFD) fd.real();
        int fdnum = real.fd;
        return attachments[fdnum].ops;
    }

    @Override
    public Object attachment(FD fd) {
        checkOpen();
        checkFDMatch(fd, false);
        var real = (PosixFD) fd.real();
        int fdnum = real.fd;
        return attachments[fdnum].att;
    }

    @Override
    public Collection<RegisterEntry> entries() {
        checkOpen();
        List<RegisterEntry> ret = new ArrayList<>(attachments.length / 2 + 1);
        for (Att att : attachments) {
            if (att.fd == null) // for the internal pipe fds
                continue;
            ret.add(new RegisterEntry(att.fd, att.ops, att.att));
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
            bufferForPipeFD.clean();
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

    @SuppressWarnings({"removal"})
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

    public int getFiredExtraNum() {
        return posix.aeGetFiredExtraNum(ae);
    }

    public AEFiredExtra.Array getFiredExtra() {
        return firedExtra;
    }
}
