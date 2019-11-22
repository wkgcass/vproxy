package vfd.posix;

import vfd.*;

import java.io.IOException;

public class PosixFDs implements FDs {
    private final Posix posix;

    public PosixFDs() {
        try {
            System.loadLibrary("vfdposix");
        } catch (UnsatisfiedLinkError e) {
            System.out.println("vfdposix not found, requires libvfdposix.dylib or libvfdposix.so or vfdposix.dll on java.library.path");
            System.exit(1);
        }
        posix = new GeneralPosix();
    }

    @Override
    public SocketFD openSocketFD() {
        return new PosixSocketFD(posix);
    }

    @Override
    public ServerSocketFD openServerSocketFD() {
        return new PosixServerSocketFD(posix);
    }

    @Override
    public DatagramFD openDatagramFD() {
        return new PosixDatagramFD(posix);
    }

    @Override
    public FDSelector openSelector() throws IOException {
        int[] pipeFd = null;
        if (posix.pipeFDSupported()) {
            pipeFd = posix.openPipe();
        }
        long ae;
        try {
            ae = posix.aeCreateEventLoop(1024);
        } catch (IOException e) {
            if (pipeFd != null) {
                try {
                    posix.close(pipeFd[0]);
                } catch (IOException ignore) {
                }
                try {
                    posix.close(pipeFd[1]);
                } catch (IOException ignore) {
                }
            }
            throw e;
        }
        return new AESelector(posix, ae, pipeFd);
    }

    @Override
    public long currentTimeMillis() {
        return posix.currentTimeMillis();
    }
}
