package vproxy.vswitch.iface;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Annotations;
import vproxy.base.util.Logger;
import vproxy.vfd.AbstractDatagramFD;
import vproxy.vfd.DatagramFD;
import vproxy.vfd.TapDatagramFD;
import vproxy.vpacket.VXLanPacket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class TapIface implements Iface {
    public final String devPattern;
    public final TapDatagramFD tap;
    public final int localSideVni;
    public final String postScript;
    public final Annotations annotations;

    private final AbstractDatagramFD<?> operateTap;
    private final SelectorEventLoop bondLoop;

    public TapIface(String devPattern, TapDatagramFD tap, AbstractDatagramFD<?> operateTap,
                    int localSideVni, String postScript, Annotations annotations,
                    SelectorEventLoop bondLoop) {
        this.devPattern = devPattern;
        this.tap = tap;
        this.localSideVni = localSideVni;
        this.postScript = postScript;
        if (annotations == null) {
            annotations = new Annotations();
        }
        this.annotations = annotations;
        this.operateTap = operateTap;
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
        return "Iface(tap:" + tap.getTap().dev + ",vni:" + localSideVni + ")";
    }

    @Override
    public void sendPacket(DatagramFD serverUDPSock, VXLanPacket vxlan, ByteBuffer writeBuf) throws IOException {
        var bytes = vxlan.getPacket().getRawPacket().toJavaArray();
        writeBuf.put(bytes);
        writeBuf.flip();
        operateTap.write(writeBuf);
    }

    @Override
    public void destroy() {
        try {
            bondLoop.remove(operateTap);
        } catch (Throwable ignore) {
        }
        try {
            operateTap.close();
        } catch (IOException e) {
            Logger.shouldNotHappen("closing tap device failed", e);
        }
    }

    @Override
    public int getLocalSideVni(int hint) {
        return localSideVni;
    }
}
