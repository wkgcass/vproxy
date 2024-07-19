package io.vproxy.vfd.posix;

import io.vproxy.base.util.Utils;
import io.vproxy.vfd.*;

import java.io.IOException;
import java.lang.reflect.Proxy;

public class PosixFDs implements FDs, FDsWithTap, FDsWithOpts, FDsWithCoreAffinity {
    public final Posix posix;

    public PosixFDs() {
        String lib = "vfdposix";
        try {
            Utils.loadDynamicLibrary(lib);
        } catch (UnsatisfiedLinkError e) {
            System.out.println(lib + " not found, requires lib" + lib + ".dylib or lib" + lib + ".so or " + lib + ".dll on java.library.path");
            e.printStackTrace(System.out);
            Utils.exit(1);
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
            posix = (Posix) Proxy.newProxyInstance(Posix.class.getClassLoader(), new Class[]{cls}, new TraceInvocationHandler(new GeneralPosix()));
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
        return openSelector(Options.defaultValue());
    }

    @Override
    public FDSelector openSelector(Options opts) throws IOException {
        int[] pipeFd = posix.openPipe();
        long ae;
        try {
            ae = posix.aeCreateEventLoop(VFDConfig.aesetsize, opts.epfd(), opts.preferPoll());
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
        return new AESelector(posix, ae, pipeFd, VFDConfig.aesetsize);
    }

    @Override
    public long currentTimeMillis() {
        return posix.currentTimeMillis();
    }

    @Override
    public boolean isV4V6DualStack() {
        return false;
    }

    public UnixDomainServerSocketFD openUnixDomainServerSocketFD() throws IOException {
        return new UnixDomainServerSocketFD(posix);
    }

    public UnixDomainSocketFD openUnixDomainSocketFD() throws IOException {
        return new UnixDomainSocketFD(posix);
    }

    @Override
    public TapDatagramFD openTap(String devPattern) throws IOException {
        TapInfo info = posix.createTapFD(devPattern, false);
        return new PosixTapDatagramFD(posix, info, false);
    }

    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return posix.tapNonBlockingSupported();
    }

    @Override
    public TapDatagramFD openTun(String devPattern) throws IOException {
        TapInfo info = posix.createTapFD(devPattern, true);
        return new PosixTapDatagramFD(posix, info, true);
    }

    @Override
    public boolean tunNonBlockingSupported() throws IOException {
        return posix.tunNonBlockingSupported();
    }

    @Override
    public void setCoreAffinity(long mask) throws IOException {
        posix.setCoreAffinityForCurrentThread(mask);
    }
}
