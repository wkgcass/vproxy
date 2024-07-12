package io.vproxy.vfd.windows;

import io.vproxy.base.util.Utils;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.*;
import io.vproxy.vfd.posix.GeneralPosix;
import io.vproxy.vfd.posix.Posix;
import io.vproxy.vfd.posix.PosixNative;

import java.io.IOException;
import java.lang.reflect.Proxy;

public class WindowsFDs implements FDs, FDsWithTap {
    private final Windows windows;
    private final Posix posix;

    public WindowsFDs() {
        String lib = "vfdwindows";
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
            String clsStr = this.getClass().getPackage().getName() + "." + this.getClass().getSimpleName().substring(0, "Windows".length());
            // clsStr should be vfd.posix.Posix
            Class<?> cls;
            try {
                cls = Class.forName(clsStr);
            } catch (ClassNotFoundException e) {
                // should not happen
                throw new RuntimeException(e);
            }
            windows = (Windows) Proxy.newProxyInstance(Windows.class.getClassLoader(), new Class[]{cls}, new TraceInvocationHandler(new GeneralWindows()));

            clsStr = Posix.class.getPackage().getName() + "." + PosixNative.class.getSimpleName().substring(0, "Posix".length());
            try {
                cls = Class.forName(clsStr);
            } catch (ClassNotFoundException e) {
                // should not happen
                throw new RuntimeException(e);
            }
            posix = (Posix) Proxy.newProxyInstance(Posix.class.getClassLoader(), new Class[]{cls}, new TraceInvocationHandler(new GeneralPosix()));
        } else {
            windows = new GeneralWindows();
            posix = new GeneralPosix();
        }
    }

    @Override
    public SocketFD openSocketFD() throws IOException {
        return new WindowsSocketFD(windows, posix);
    }

    @Override
    public ServerSocketFD openServerSocketFD() throws IOException {
        return new WindowsServerSocketFD(windows, posix);
    }

    @Override
    public DatagramFD openDatagramFD() throws IOException {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public FDSelector openSelector() throws IOException {
        return new IOCPSelector(new WinIOCP(1));
    }

    @Override
    public long currentTimeMillis() {
        return PosixNative.get().currentTimeMillis(VProxyThread.current().getEnv());
    }

    @Override
    public boolean isV4V6DualStack() {
        return false;
    }

    @Override
    public TapDatagramFD openTap(String dev) throws IOException {
        var handle = windows.createTapHandle(dev);
        var socket = WinSocket.ofDatagram((int) handle.MEMORY.address());
        return new WindowsTapDatagramFD(windows, posix,
            socket, new TapInfo(dev, (int) handle.MEMORY.address()));
    }

    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return windows.tapNonBlockingSupported();
    }

    @Override
    public TapDatagramFD openTun(String devPattern) throws IOException {
        throw new IOException("tun unsupported");
    }

    @Override
    public boolean tunNonBlockingSupported() throws IOException {
        throw new IOException("tun unsupported");
    }
}
