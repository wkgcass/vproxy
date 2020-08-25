package vswitch.iface;

import vfd.AbstractDatagramFD;
import vfd.DatagramFD;
import vfd.TapDatagramFD;
import vpacket.VXLanPacket;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class TapIface implements Iface {
    public final TapDatagramFD tap;
    public final int localSideVni;
    public final String postScript;
    public final Map<String, String> annotations;

    private final AbstractDatagramFD<?> operateTap;
    private final SelectorEventLoop bondLoop;

    public TapIface(TapDatagramFD tap, AbstractDatagramFD<?> operateTap, int localSideVni, String postScript, Map<String, String> annotations,
                    SelectorEventLoop bondLoop) {
        this.tap = tap;
        this.localSideVni = localSideVni;
        this.postScript = postScript;
        if (annotations == null) {
            annotations = Collections.emptyMap();
        }
        this.annotations = Collections.unmodifiableMap(annotations);
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
