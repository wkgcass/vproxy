package io.vproxy.vswitch.iface;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.vfd.DatagramFD;
import io.vproxy.vfd.IPPort;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.util.SwitchUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AbstractBaseSwitchSocketIface extends Iface {
    protected DatagramFD sock;
    public final IPPort remote;
    protected final ByteBuffer sndBuf = Utils.allocateByteBuffer(2048);

    protected AbstractBaseSwitchSocketIface(IPPort remote) {
        this.remote = remote;
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        super.init(params);
        this.sock = params.sock;
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public void sendPacket(PacketBuffer pkb) {
        assert Logger.lowLevelDebug(this + ".sendPacket(" + pkb + ")");

        var vxlan = SwitchUtils.getOrMakeVXLanPacket(pkb);

        sndBuf.limit(sndBuf.capacity()).position(0);

        byte[] bytes = vxlan.getRawPacket(0).toJavaArray();
        sndBuf.put(bytes);
        sndBuf.flip();

        manipulate();

        statistics.incrTxPkts();
        statistics.incrTxBytes(sndBuf.limit() - sndBuf.position());

        try {
            sock.send(sndBuf, remote);
        } catch (IOException e) {
            assert Logger.lowLevelDebug("sending packet to " + this + " failed: " + e);
            statistics.incrTxErr();
        }
    }

    protected void manipulate() {
        // do nothing
    }
}
