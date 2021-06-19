package vproxy.vswitch.stack;

import vproxy.base.util.Consts;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.vfd.IP;
import vproxy.vfd.MacAddress;
import vproxy.vswitch.SocketBuffer;
import vproxy.vswitch.SwitchContext;
import vproxy.vswitch.Table;

public class NetworkStack {
    private final L2 L2;
    public final L4 L4;

    public NetworkStack(SwitchContext swCtx) {
        this.L2 = new L2(swCtx);
        this.L4 = this.L2.L3.L4;
    }

    public void devInput(SocketBuffer skb) {
        if (loopDetect(skb)) {
            return;
        }

        L2.input(skb);
    }

    private boolean loopDetect(SocketBuffer skb) {
        var vxlan = skb.vxlan;
        if (vxlan == null) {
            return false;
        }
        int r1 = vxlan.getReserved1();
        int r2 = vxlan.getReserved2();

        if (r2 > 250) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "possible loop detected from " + skb.devin + " with packet " + vxlan);

            final int I_DETECTED_A_POSSIBLE_LOOP = Consts.I_DETECTED_A_POSSIBLE_LOOP;
            final int I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN = Consts.I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN;

            boolean possibleLoop = (r1 & I_DETECTED_A_POSSIBLE_LOOP) == I_DETECTED_A_POSSIBLE_LOOP;
            boolean willDisconnect = (r1 & I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN) == I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN;

            if (possibleLoop && willDisconnect) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "disconnect from " + skb.devin + " due to possible loop");
                skb.table.macTable.disconnect(skb.devin);
                return true; // drop
            }
            if (!possibleLoop && !willDisconnect) {
                vxlan.setReserved1(r1 | I_DETECTED_A_POSSIBLE_LOOP);
            } else {
                vxlan.setReserved1(r1 | I_DETECTED_A_POSSIBLE_LOOP | I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN);
            }
        }
        vxlan.setReserved2(r2 + 1);

        return false;
    }

    public void resolve(Table t, IP ip, MacAddress mac) {
        L2.L3.resolve(t, ip, mac);
    }
}
