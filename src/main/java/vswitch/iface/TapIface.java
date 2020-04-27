package vswitch.iface;

import vfd.DatagramFD;
import vfd.posix.TunTapDatagramFD;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.Logger;
import vswitch.packet.VXLanPacket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class TapIface implements Iface {
    public final TunTapDatagramFD tap;
    public final int serverSideVni;

    private final SelectorEventLoop bondLoop;

    public TapIface(TunTapDatagramFD tap, int serverSideVni, SelectorEventLoop bondLoop) {
        this.tap = tap;
        this.serverSideVni = serverSideVni;
        this.bondLoop = bondLoop;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TapIface tapIface = (TapIface) o;
        return Objects.equals(tap, tapIface.tap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tap);
    }

    @Override
    public String toString() {
        return "Iface(" + tap.tuntap.dev + ",vni:" + serverSideVni + ")";
    }

    @Override
    public void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException {
        var bytes = vxlan.getPacket().getRawPacket().toJavaArray();
        writeBuf.put(bytes);
        writeBuf.flip();
        tap.write(writeBuf);
    }

    @Override
    public void destroy() {
        try {
            bondLoop.remove(tap);
        } catch (Throwable ignore) {
        }
        try {
            tap.close();
        } catch (IOException e) {
            Logger.shouldNotHappen("closing tap device failed", e);
        }
    }

    @Override
    public int getServerSideVni(int hint) {
        return serverSideVni;
    }
}
