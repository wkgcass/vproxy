package vfd.posix;

import vproxy.util.Tuple;

import java.io.IOException;
import java.nio.ByteBuffer;

public class GeneralPosix implements Posix {
    @Override
    native public boolean pipeFDSupported();

    @Override
    native public boolean onlySelectNow();

    @Override
    native public int aeReadable();

    @Override
    native public int aeWritable();

    @Override
    native public int[] openPipe() throws IOException;

    @Override
    native public long aeCreateEventLoop(int setsize) throws IOException;

    @Override
    native public FDInfo[] aeApiPoll(long ae, long wait) throws IOException;

    @Override
    native public FDInfo[] aeAllFDs(long ae);

    @Override
    native public void aeCreateFileEvent(long ae, int fd, int mask, Object clientData);

    @Override
    native public void aeUpdateFileEvent(long ae, int fd, int mask);

    @Override
    native public void aeDeleteFileEvent(long ae, int fd);

    @Override
    native public int aeGetFileEvents(long ae, int fd);

    @Override
    native public Object aeGetClientData(long ae, int fd);

    @Override
    native public void aeDeleteEventLoop(long ae);

    @Override
    native public void setBlocking(int fd, boolean v) throws IOException;

    @Override
    native public void setSoLinger(int fd, int v) throws IOException;

    @Override
    native public void setReusePort(int fd, boolean v) throws IOException;

    @Override
    native public void setTcpNoDelay(int fd, boolean v) throws IOException;

    @Override
    native public void close(int fd) throws IOException;

    @Override
    native public int createIPv4TcpFD() throws IOException;

    @Override
    native public int createIPv6TcpFD() throws IOException;

    @Override
    native public int createIPv4UdpFD() throws IOException;

    @Override
    native public int createIPv6UdpFD() throws IOException;

    @Override
    native public void bindIPv4(int fd, int addrHostOrder, int port) throws IOException;

    @Override
    native public void bindIPv6(int fd, String fullAddr, int port) throws IOException;

    @Override
    native public int accept(int fd) throws IOException;

    @Override
    native public void connectIPv4(int fd, int addrHostOrder, int port) throws IOException;

    @Override
    native public void connectIPv6(int fd, String fullAddr, int port) throws IOException;

    @Override
    native public void shutdownOutput(int fd) throws IOException;

    @Override
    native public SocketAddressIPv4 getIPv4Local(int fd) throws IOException;

    @Override
    native public SocketAddressIPv6 getIPv6Local(int fd) throws IOException;

    @Override
    native public SocketAddressIPv4 getIPv4Remote(int fd) throws IOException;

    @Override
    native public SocketAddressIPv6 getIPv6Remote(int fd) throws IOException;

    @Override
    native public int read(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public int write(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public int sendtoIPv4(int fd, ByteBuffer directBuffer, int off, int len, int addrHostOrder, int port) throws IOException;

    @Override
    native public int sendtoIPv6(int fd, ByteBuffer directBuffer, int off, int len, String fullAddr, int port) throws IOException;

    @Override
    native public Tuple<SocketAddressIPv4, Integer> recvfromIPv4(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public Tuple<SocketAddressIPv6, Integer> recvfromIPv6(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public long currentTimeMillis();
}
