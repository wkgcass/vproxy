package vproxy.vswitch.iface;

import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.vfd.DatagramFD;
import vproxy.vfd.IPPort;
import vproxy.vswitch.SocketBuffer;
import vproxy.vswitch.util.SwitchUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AbstractBaseSwitchSocketIface extends AbstractIface implements Iface {
    protected DatagramFD sock;
    protected boolean sockConnected = false; // default sock is the server sock in switch, so it's not connected
    public final IPPort remote;
    protected final ByteBuffer sndBuf = Utils.allocateByteBuffer(2048);

    protected AbstractBaseSwitchSocketIface(IPPort remote) {
        this.remote = remote;
    }

    @Override
    public void init(IfaceInitParams params) throws Exception {
        this.sock = params.sock;
    }

    protected void setSock(DatagramFD sock, boolean connected) {
        this.sock = sock;
        this.sockConnected = connected;
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public void sendPacket(SocketBuffer skb) {
        assert Logger.lowLevelDebug(this + ".sendPacket(" + skb + ")");

        var vxlan = SwitchUtils.getOrMakeVXLanPacket(skb);

        sndBuf.limit(sndBuf.capacity()).position(0);

        byte[] bytes = vxlan.getRawPacket().toJavaArray();
        sndBuf.put(bytes);
        sndBuf.flip();

        manipulate();

        try {
            if (sockConnected) {
                sock.write(sndBuf);
            } else {
                sock.send(sndBuf, remote);
            }
        } catch (IOException e) {
            Logger.error(LogType.CONN_ERROR, "sending packet to " + this + " failed", e);
        }
    }

    protected void manipulate() {
        // do nothing
    }
}
