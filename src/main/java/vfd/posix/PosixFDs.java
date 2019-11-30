package vfd.posix;

import vfd.*;

import java.io.IOException;
import java.lang.reflect.Proxy;

public class PosixFDs implements FDs {
    private final Posix posix;

    public PosixFDs() {
        assert VFDConfig.vfdlibname != null;
        String lib = VFDConfig.vfdlibname;
        try {
            System.loadLibrary(lib);
        } catch (UnsatisfiedLinkError e) {
            System.out.println(lib + " not found, requires lib" + lib + ".dylib or lib" + lib + ".so or " + lib + ".dll on java.library.path");
            e.printStackTrace(System.out);
            System.exit(1);
        }
        if (VFDConfig.vfdtrace) {
            posix = (Posix) Proxy.newProxyInstance(Posix.class.getClassLoader(), new Class[]{Posix.class}, new TracePosixInvocationHandler(new GeneralPosix()));
        } else {
            posix = new GeneralPosix();
        }
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
            ae = posix.aeCreateEventLoop(1024 * 1024);
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
