package vfd.posix;

import vfd.*;

import java.io.IOException;
import java.lang.reflect.Proxy;

public class PosixFDs implements FDs {
    public final Posix posix;

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
            // make it difficult for graalvm native image initializer to detect the Posix.class
            // however we cannot use -Dvfdtrace=1 flag when using native image
            String clsStr = this.getClass().getPackage().getName() + "." + this.getClass().getSimpleName().substring(0, "Posix".length());
            // clsStr should be vfd.posix.Posix
            Class<?> cls;
            try {
                cls = Class.forName(clsStr);
            } catch (ClassNotFoundException e) {
                // should not happen
                throw new RuntimeException(e);
            }
            posix = (Posix) Proxy.newProxyInstance(Posix.class.getClassLoader(), new Class[]{cls}, new TracePosixInvocationHandler(new GeneralPosix()));
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

    public TapDatagramFD openTap(String devPattern) throws IOException {
        TapInfo info = posix.createTapFD(devPattern);
        return new TapDatagramFD(posix, info);
    }
}
