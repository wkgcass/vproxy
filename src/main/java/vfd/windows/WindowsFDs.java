package vfd.windows;

import vfd.*;
import vfd.jdk.ChannelFDs;
import vfd.posix.Posix;
import vproxy.util.Logger;
import vproxy.util.Utils;

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
            System.loadLibrary(lib);
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
    public TapDatagramFD openTap(String dev) throws IOException {
        long handle = windows.createTapHandle(dev);
        long readOverlapped;
        try {
            readOverlapped = windows.allocateOverlapped();
        } catch (IOException e) {
            try {
                windows.closeHandle(handle);
            } catch (Throwable t) {
                Logger.shouldNotHappen("close handle " + handle + " failed when allocating readOverlapped failed", t);
            }
            throw e;
        }
        long writeOverlapped;
        try {
            writeOverlapped = windows.allocateOverlapped();
        } catch (IOException e) {
            try {
                windows.closeHandle(handle);
            } catch (Throwable t) {
                Logger.shouldNotHappen("close handle " + handle + " failed when allocating writeOverlapped failed", t);
            }
            try {
                windows.releaseOverlapped(readOverlapped);
            } catch (Throwable t) {
                Logger.shouldNotHappen("releasing readOverlapped " + readOverlapped + " failed when allocating writeOverlapped failed", t);
            }
            throw e;
        }
        return new WindowsTapDatagramFD(windows, handle, new TapInfo(dev, (int) handle), readOverlapped, writeOverlapped);
    }

    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return windows.tapNonBlockingSupported();
    }
}
