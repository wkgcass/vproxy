package io.vproxy.vfd.posix;

import io.vproxy.panama.WrappedFunction;
import io.vproxy.panama.Panama;
import io.vproxy.vfd.TapInfo;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

import static io.vproxy.panama.Panama.format;

public class GeneralPosix implements Posix {
    public GeneralPosix() {
    }

    private static final WrappedFunction aeReadable =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_aeReadable");

    @Override
    public int aeReadable() {
        return aeReadable.invoke((h, e) ->
            (int) h.invokeExact(e)
        ).returnInt();
    }

    private static final WrappedFunction aeWritable =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_aeWritable");

    @Override
    public int aeWritable() {
        return aeWritable.invoke((h, e) ->
            (int) h.invokeExact(e)
        ).returnInt();
    }

    private static final WrappedFunction openPipe =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_openPipe",
            MemorySegment.class);

    @Override
    public int[] openPipe() throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg = arena.allocate(ValueLayout.JAVA_INT.byteSize() * 2);
            openPipe.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, seg)
            );
            int _1 = seg.get(ValueLayout.JAVA_INT, 0);
            int _2 = seg.get(ValueLayout.JAVA_INT, 4);
            return new int[]{_1, _2};
        }
    }

    private static final WrappedFunction aeCreateEventLoop =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateEventLoop",
            int.class, boolean.class);

    @Override
    public long aeCreateEventLoop(int setsize, boolean preferPoll) throws IOException {
        return aeCreateEventLoop.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, setsize, preferPoll)
        ).returnLong();
    }

    private static final WrappedFunction aeApiPoll =
        Panama.get().lookupWrappedFunction(false, "Java_io_vproxy_vfd_posix_GeneralPosix_aeApiPoll",
            Object.class, long.class, MemorySegment.class, MemorySegment.class);

    @Override
    public int aeApiPoll(long ae, long wait, MemorySegment fdArray, MemorySegment eventsArray) throws IOException {
        var aex = MemorySegment.ofAddress(ae);
        return aeApiPoll.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, aex, wait, fdArray, eventsArray)
        ).returnInt();
    }

    private static final WrappedFunction aeApiPollNow =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_aeApiPollNow",
            Object.class, MemorySegment.class, MemorySegment.class);

    @Override
    public int aeApiPollNow(long ae, MemorySegment fdArray, MemorySegment eventsArray) throws IOException {
        var aex = MemorySegment.ofAddress(ae);
        return aeApiPollNow.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, aex, fdArray, eventsArray)
        ).returnInt();
    }

    private static final WrappedFunction aeCreateFileEvent =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent",
            Object.class, int.class, int.class);

    @Override
    public void aeCreateFileEvent(long ae, int fd, int mask) {
        var aex = MemorySegment.ofAddress(ae);
        aeCreateFileEvent.invoke((h, e) ->
            (int) h.invokeExact(e, aex, fd, mask)
        );
    }

    private static final WrappedFunction aeUpdateFileEvent =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent",
            Object.class, int.class, int.class);

    @Override
    public void aeUpdateFileEvent(long ae, int fd, int mask) {
        var aex = MemorySegment.ofAddress(ae);
        aeUpdateFileEvent.invoke((h, e) ->
            (int) h.invokeExact(e, aex, fd, mask)
        );
    }

    private static final WrappedFunction aeDeleteFileEvent =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent",
            Object.class, int.class);

    @Override
    public void aeDeleteFileEvent(long ae, int fd) {
        var aex = MemorySegment.ofAddress(ae);
        aeDeleteFileEvent.invoke((h, e) ->
            (int) h.invokeExact(e, aex, fd)
        );
    }

    private static final WrappedFunction aeDeleteEventLoop =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteEventLoop",
            Object.class);

    @Override
    public void aeDeleteEventLoop(long ae) {
        var aex = MemorySegment.ofAddress(ae);
        aeDeleteEventLoop.invoke((h, e) ->
            (int) h.invokeExact(e, aex)
        );
    }

    private static final WrappedFunction setBlocking =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_setBlocking",
            int.class, boolean.class);

    @Override
    public void setBlocking(int fd, boolean v) throws IOException {
        setBlocking.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, v)
        );
    }

    private static final WrappedFunction setSoLinger =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_setSoLinger",
            int.class, int.class);

    @Override
    public void setSoLinger(int fd, int v) throws IOException {
        setSoLinger.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, v)
        );
    }

    private static final WrappedFunction setReusePort =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_setReusePort",
            int.class, boolean.class);

    @Override
    public void setReusePort(int fd, boolean v) throws IOException {
        setReusePort.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, v)
        );
    }

    private static final WrappedFunction setRcvBuf =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_setRcvBuf",
            int.class, int.class);

    @Override
    public void setRcvBuf(int fd, int buflen) throws IOException {
        setRcvBuf.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, buflen)
        );
    }

    private static final WrappedFunction setTcpNoDelay =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_setTcpNoDelay",
            int.class, boolean.class);

    @Override
    public void setTcpNoDelay(int fd, boolean v) throws IOException {
        setTcpNoDelay.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, v)
        );
    }

    private static final WrappedFunction setBroadcast =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_setBroadcast",
            int.class, boolean.class);

    @Override
    public void setBroadcast(int fd, boolean v) throws IOException {
        setBroadcast.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, v)
        );
    }

    private static final WrappedFunction setIpTransparent =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_setIpTransparent",
            int.class, boolean.class);

    @Override
    public void setIpTransparent(int fd, boolean v) throws IOException {
        setIpTransparent.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, v)
        );
    }

    private static final WrappedFunction close =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_close",
            int.class);

    @Override
    public void close(int fd) throws IOException {
        close.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd)
        );
    }

    private static final WrappedFunction createIPv4TcpFD =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_createIPv4TcpFD");

    @Override
    public int createIPv4TcpFD() throws IOException {
        return createIPv4TcpFD.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnInt();
    }

    private static final WrappedFunction createIPv6TcpFD =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_createIPv6TcpFD");

    @Override
    public int createIPv6TcpFD() throws IOException {
        return createIPv6TcpFD.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnInt();
    }

    private static final WrappedFunction createIPv4UdpFD =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_createIPv4UdpFD");

    @Override
    public int createIPv4UdpFD() throws IOException {
        return createIPv4UdpFD.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnInt();
    }

    private static final WrappedFunction createIPv6UdpFD =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_createIPv6UdpFD");

    @Override
    public int createIPv6UdpFD() throws IOException {
        return createIPv6UdpFD.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnInt();
    }

    private static final WrappedFunction createUnixDomainSocketFD =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_createUnixDomainSocketFD");

    @Override
    public int createUnixDomainSocketFD() throws IOException {
        return createUnixDomainSocketFD.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnInt();
    }

    private static final WrappedFunction bindIPv4 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_bindIPv4",
            int.class, int.class, int.class);

    @Override
    public void bindIPv4(int fd, int addrHostOrder, int port) throws IOException {
        bindIPv4.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, addrHostOrder, port)
        );
    }

    private static final WrappedFunction bindIPv6 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_bindIPv6",
            int.class, String.class, int.class);

    @Override
    public void bindIPv6(int fd, String fullAddr, int port) throws IOException {
        try (var arena = Arena.ofConfined()) {
            bindIPv6.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, format(fullAddr, arena), port)
            );
        }
    }

    private static final WrappedFunction bindUnixDomainSocket =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_bindUnixDomainSocket",
            int.class, String.class);

    @Override
    public void bindUnixDomainSocket(int fd, String path) throws IOException {
        try (var arena = Arena.ofConfined()) {
            bindUnixDomainSocket.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, format(path, arena))
            );
        }
    }

    private static final WrappedFunction accept =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_accept",
            int.class);

    @Override
    public int accept(int fd) throws IOException {
        return accept.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd)
        ).returnInt();
    }

    private static final WrappedFunction connectIPv4 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_connectIPv4",
            int.class, int.class, int.class);

    @Override
    public void connectIPv4(int fd, int addrHostOrder, int port) throws IOException {
        connectIPv4.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, addrHostOrder, port)
        );
    }

    private static final WrappedFunction connectIPv6 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_connectIPv6",
            int.class, String.class, int.class);

    @Override
    public void connectIPv6(int fd, String fullAddr, int port) throws IOException {
        try (var arena = Arena.ofConfined()) {
            connectIPv6.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, format(fullAddr, arena), port)
            );
        }
    }

    private static final WrappedFunction connectUDS =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_connectUDS",
            int.class, String.class);

    @Override
    public void connectUDS(int fd, String sock) throws IOException {
        try (var arena = Arena.ofConfined()) {
            connectUDS.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, format(sock, arena))
            );
        }
    }

    private static final WrappedFunction finishConnect =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_finishConnect",
            int.class);

    @Override
    public void finishConnect(int fd) throws IOException {
        finishConnect.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd)
        );
    }

    private static final WrappedFunction shutdownOutput =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_shutdownOutput",
            int.class);

    @Override
    public void shutdownOutput(int fd) throws IOException {
        shutdownOutput.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd)
        );
    }

    private static final MemoryLayout SocketAddressIPv4 = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT_UNALIGNED.withName("ip"),
        ValueLayout.JAVA_SHORT_UNALIGNED.withName("port")
    );
    private static final VarHandle SocketAddressIPv4_ip = SocketAddressIPv4.varHandle(
        MemoryLayout.PathElement.groupElement("ip")
    );
    private static final VarHandle SocketAddressIPv4_port = SocketAddressIPv4.varHandle(
        MemoryLayout.PathElement.groupElement("port")
    );

    private static final WrappedFunction getIPv4Local =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_getIPv4Local",
            int.class, SocketAddressIPv4.getClass());

    @Override
    public VSocketAddress getIPv4Local(int fd) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(SocketAddressIPv4.byteSize());
            var seg = getIPv4Local.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(SocketAddressIPv4.byteSize());
            }
            return buildIPv4SocketAddress(seg);
        }
    }

    private VSocketAddress buildIPv4SocketAddress(MemorySegment seg) {
        if (seg == null) {
            return null;
        }
        var ip = (int) SocketAddressIPv4_ip.get(seg);
        var port = (short) SocketAddressIPv4_port.get(seg);
        return new SocketAddressIPv4(ip, port & 0xffff);
    }

    private static final MemoryLayout SocketAddressIPv6 = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(40, ValueLayout.JAVA_BYTE).withName("ip"),
        ValueLayout.JAVA_SHORT_UNALIGNED.withName("port")
    );
    private static final VarHandle SocketAddressIPv6_port = SocketAddressIPv6.varHandle(
        MemoryLayout.PathElement.groupElement("port")
    );

    private static final WrappedFunction getIPv6Local =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_getIPv6Local",
            int.class, SocketAddressIPv6.getClass());

    @Override
    public VSocketAddress getIPv6Local(int fd) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(SocketAddressIPv6.byteSize());
            var seg = getIPv6Local.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(SocketAddressIPv6.byteSize());
            }
            return buildIPv6SocketAddress(seg);
        }
    }

    private VSocketAddress buildIPv6SocketAddress(MemorySegment seg) {
        if (seg == null) {
            return null;
        }
        var ip = seg.asSlice(0, 40);
        var port = (short) SocketAddressIPv6_port.get(seg);
        return new SocketAddressIPv6(ip.getUtf8String(0), port & 0xffff);
    }

    private static final WrappedFunction getIPv4Remote =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_getIPv4Remote",
            int.class, SocketAddressIPv4.getClass());

    @Override
    public VSocketAddress getIPv4Remote(int fd) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(SocketAddressIPv4.byteSize());
            var seg = getIPv4Remote.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(SocketAddressIPv4.byteSize());
            }
            return buildIPv4SocketAddress(seg);
        }
    }

    private static final WrappedFunction getIPv6Remote =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_getIPv6Remote",
            int.class, SocketAddressIPv6.getClass());

    @Override
    public VSocketAddress getIPv6Remote(int fd) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(SocketAddressIPv6.byteSize());
            var seg = getIPv6Remote.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(SocketAddressIPv6.byteSize());
            }
            return buildIPv6SocketAddress(seg);
        }
    }

    private static final MemoryLayout SocketAddressUDS = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(4096, ValueLayout.JAVA_BYTE).withName("path")
    );

    private static final WrappedFunction getUDSLocal =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_getUDSLocal",
            int.class, SocketAddressUDS.getClass());

    @Override
    public VSocketAddress getUDSLocal(int fd) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(SocketAddressUDS.byteSize());
            var seg = getUDSLocal.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(SocketAddressUDS.byteSize());
            }
            return buildUDSSocketAddress(seg);
        }
    }

    private VSocketAddress buildUDSSocketAddress(MemorySegment seg) {
        if (seg == null) {
            return null;
        }
        var path = seg.asSlice(0, 4096);
        return new SocketAddressUDS(path.getUtf8String(0));
    }

    private static final WrappedFunction getUDSRemote =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_getUDSRemote",
            int.class, SocketAddressUDS.getClass());

    @Override
    public VSocketAddress getUDSRemote(int fd) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(SocketAddressUDS.byteSize());
            var seg = getUDSRemote.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(SocketAddressUDS.byteSize());
            }
            return buildUDSSocketAddress(seg);
        }
    }

    private static final WrappedFunction read =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_read",
            int.class, ByteBuffer.class, int.class, int.class);

    @Override
    public int read(int fd, ByteBuffer directBuffer, int off, int len) throws IOException {
        return read.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, format(directBuffer), off, len)
        ).returnInt();
    }

    private static final WrappedFunction write =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_write",
            int.class, ByteBuffer.class, int.class, int.class);

    @Override
    public int write(int fd, ByteBuffer directBuffer, int off, int len) throws IOException {
        return write.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, format(directBuffer), off, len)
        ).returnInt();
    }

    private static final WrappedFunction sendtoIPv4 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_sendtoIPv4",
            int.class, ByteBuffer.class, int.class, int.class, int.class, int.class);

    @Override
    public int sendtoIPv4(int fd, ByteBuffer directBuffer, int off, int len, int addrHostOrder, int port) throws IOException {
        return sendtoIPv4.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, fd, format(directBuffer), off, len, addrHostOrder, port)
        ).returnInt();
    }

    private static final WrappedFunction sendtoIPv6 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_sendtoIPv6",
            int.class, ByteBuffer.class, int.class, int.class, String.class, int.class);

    @Override
    public int sendtoIPv6(int fd, ByteBuffer directBuffer, int off, int len, String fullAddr, int port) throws IOException {
        try (var arena = Arena.ofConfined()) {
            return sendtoIPv6.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, format(directBuffer), off, len, format(fullAddr, arena), port)
            ).returnInt();
        }
    }

    private static final MemoryLayout UDPRecvResultIPv4 = MemoryLayout.structLayout(
        SocketAddressIPv4.withName("addr"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("len")
    );
    private static final VarHandle UDPRecvResultIPv4_len = UDPRecvResultIPv4.varHandle(
        MemoryLayout.PathElement.groupElement("len")
    );

    private static final WrappedFunction recvfromIPv4 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_recvfromIPv4",
            int.class, ByteBuffer.class, int.class, int.class, UDPRecvResultIPv4.getClass());

    @Override
    public UDPRecvResult recvfromIPv4(int fd, ByteBuffer directBuffer, int off, int len) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(UDPRecvResultIPv4.byteSize());
            var seg = recvfromIPv4.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, format(directBuffer), off, len, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(UDPRecvResultIPv4.byteSize());
            }
            return buildIPv4UDPRecvResult(seg);
        }
    }

    private UDPRecvResult buildIPv4UDPRecvResult(MemorySegment seg) {
        if (seg == null) {
            return null;
        }
        var addrSeg = seg.asSlice(0, SocketAddressIPv4.byteSize());
        var addr = buildIPv4SocketAddress(addrSeg);
        var len = (int) UDPRecvResultIPv4_len.get(seg);
        return new UDPRecvResult(addr, len);
    }

    private static final MemoryLayout UDPRecvResultIPv6 = MemoryLayout.structLayout(
        SocketAddressIPv6.withName("addr"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("len")
    );
    private static final VarHandle UDPRecvResultIPv6_len = UDPRecvResultIPv6.varHandle(
        MemoryLayout.PathElement.groupElement("len")
    );

    private static final WrappedFunction recvfromIPv6 =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_recvfromIPv6",
            int.class, ByteBuffer.class, int.class, int.class, UDPRecvResultIPv6.getClass());

    @Override
    public UDPRecvResult recvfromIPv6(int fd, ByteBuffer directBuffer, int off, int len) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(UDPRecvResultIPv6.byteSize());
            var seg = recvfromIPv6.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, fd, format(directBuffer), off, len, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(UDPRecvResultIPv6.byteSize());
            }
            return buildIPv6UDPRecvResult(seg);
        }
    }

    private UDPRecvResult buildIPv6UDPRecvResult(MemorySegment seg) {
        if (seg == null) {
            return null;
        }
        var addrSeg = seg.asSlice(0, SocketAddressIPv6.byteSize());
        var addr = buildIPv6SocketAddress(addrSeg);
        var len = (int) UDPRecvResultIPv6_len.get(seg);
        return new UDPRecvResult(addr, len);
    }

    private static final WrappedFunction currentTimeMillis =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_currentTimeMillis");

    @Override
    public long currentTimeMillis() {
        return currentTimeMillis.invoke((h, e) ->
            (int) h.invokeExact(e)
        ).returnLong();
    }

    private static final WrappedFunction tapNonBlockingSupported =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_tapNonBlockingSupported");

    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return tapNonBlockingSupported.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnBool();
    }

    private static final WrappedFunction tunNonBlockingSupported =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_tunNonBlockingSupported");

    @Override
    public boolean tunNonBlockingSupported() throws IOException {
        return tunNonBlockingSupported.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e)
        ).returnBool();
    }

    private static final MemoryLayout TapInfo = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(16, ValueLayout.JAVA_BYTE).withName("devName"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("fd")
    );
    private static final VarHandle TapInfo_fd = TapInfo.varHandle(
        MemoryLayout.PathElement.groupElement("fd")
    );

    private static final WrappedFunction createTapFD =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_createTapFD",
            String.class, boolean.class, TapInfo.getClass());

    @Override
    public TapInfo createTapFD(String dev, boolean isTun) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var seg0 = arena.allocate(TapInfo.byteSize());
            var seg = createTapFD.invoke(IOException.class, (h, e) ->
                (int) h.invokeExact(e, format(dev, arena), isTun, seg0)
            ).returnPointer();
            if (seg != null) {
                seg = seg.reinterpret(TapInfo.byteSize());
            }
            return buildTapInfo(seg);
        }
    }

    private TapInfo buildTapInfo(MemorySegment seg) {
        if (seg == null) {
            return null;
        }
        var devSeg = seg.asSlice(0, 16);
        var dev = devSeg.getUtf8String(0);
        var fd = (int) TapInfo_fd.get(seg);
        return new TapInfo(dev, fd);
    }

    private static final WrappedFunction setCoreAffinityForCurrentThread =
        Panama.get().lookupWrappedFunction("Java_io_vproxy_vfd_posix_GeneralPosix_setCoreAffinityForCurrentThread",
            long.class);

    @Override
    public void setCoreAffinityForCurrentThread(long mask) throws IOException {
        setCoreAffinityForCurrentThread.invoke(IOException.class, (h, e) ->
            (int) h.invokeExact(e, mask)
        );
    }
}
