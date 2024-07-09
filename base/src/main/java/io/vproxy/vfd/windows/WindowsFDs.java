package io.vproxy.vfd.windows;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.*;
import io.vproxy.vfd.jdk.ChannelFDs;
import io.vproxy.vfd.posix.Posix;

import java.io.IOException;
import java.lang.reflect.Proxy;

public class WindowsFDs implements FDs, FDsWithTap {
    private final ChannelFDs channelFDs;
    private final Windows windows;

    public WindowsFDs() {
        channelFDs = ChannelFDs.get();

        assert VFDConfig.vfdlibname != null;
        String lib = VFDConfig.vfdlibname;
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
            windows = (Windows) Proxy.newProxyInstance(Posix.class.getClassLoader(), new Class[]{cls}, new TraceInvocationHandler(new GeneralWindows()));
        } else {
            windows = new GeneralWindows();
        }
    }

    @Override
    public SocketFD openSocketFD() throws IOException {
        return channelFDs.openSocketFD();
    }

    @Override
    public ServerSocketFD openServerSocketFD() throws IOException {
        return channelFDs.openServerSocketFD();
    }

    @Override
    public DatagramFD openDatagramFD() throws IOException {
        return channelFDs.openDatagramFD();
    }

    @Override
    public FDSelector openSelector() throws IOException {
        return channelFDs.openSelector();
    }

    @Override
    public long currentTimeMillis() {
        return channelFDs.currentTimeMillis();
    }

    @Override
    public boolean isV4V6DualStack() {
        return true;
    }

    @Override
    public TapDatagramFD openTap(String dev) throws IOException {
        var handle = windows.createTapHandle(dev);
        var socket = WinSocket.ofTcp((int) handle.MEMORY.address());
        return new WindowsTapDatagramFD(windows, socket, new TapInfo(dev, (int) handle.MEMORY.address()));
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
