package io.vproxy.vswitch.stack;

import io.vproxy.base.util.Consts;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchContext;
import io.vproxy.vswitch.VirtualNetwork;

public class NetworkStack {
    private final L2 L2;
    public final L4 L4;

    public NetworkStack(SwitchContext swCtx) {
        this.L2 = new L2(swCtx);
        this.L4 = this.L2.L3.L4;
    }

    public void devInput(PacketBuffer pkb) {
        if (loopDetect(pkb)) {
            return;
        }

        L2.input(pkb);
    }

    private boolean loopDetect(PacketBuffer pkb) {
        var vxlan = pkb.vxlan;
        if (vxlan == null) {
            return false;
        }
        int r1 = vxlan.getReserved1();
        int r2 = vxlan.getReserved2();

        if (r2 > 250) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "possible loop detected from " + pkb.devin + " with packet " + vxlan);

            final int I_DETECTED_A_POSSIBLE_LOOP = Consts.I_DETECTED_A_POSSIBLE_LOOP;
            final int I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN = Consts.I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN;

            boolean possibleLoop = (r1 & I_DETECTED_A_POSSIBLE_LOOP) == I_DETECTED_A_POSSIBLE_LOOP;
            boolean willDisconnect = (r1 & I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN) == I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN;

            if (possibleLoop && willDisconnect) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "disconnect from " + pkb.devin + " due to possible loop");
                pkb.network.macTable.disconnect(pkb.devin);
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

    public void resolve(VirtualNetwork t, IP ip, MacAddress mac) {
        L2.L3.resolve(t, ip, mac);
    }
}
