package io.vproxy.vswitch.node;

import io.vproxy.base.util.Consts;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.commons.graph.GraphBuilder;
import io.vproxy.vswitch.PacketBuffer;

public class VXLanLoopDetect extends Node {
    private final NodeEgress ethernetInput = new NodeEgress("ethernet-input");

    public VXLanLoopDetect() {
        super("vxlan-loop-detect");
    }

    @Override
    protected void initGraph(GraphBuilder<Node> builder) {
        builder.addEdge("dev-input", "vxlan-loop-detect", "ethernet-input", DEFAULT_EDGE_DISTANCE - BUILT_IN_DISTANCE_DELTA);
        builder.addEdge("vxlan-loop-detect", "ethernet-input", "ethernet-input", DEFAULT_EDGE_DISTANCE);
    }

    @Override
    protected void initNode() {
        fillEdges(ethernetInput);
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        if (pkb.vxlan != null) {
            return HandleResult.PASS;
        } else
            return HandleResult.CONTINUE;
    }

    @Override
    protected HandleResult handle(PacketBuffer pkb, NodeGraphScheduler scheduler) {
        var vxlan = pkb.vxlan;
        if (vxlan == null) {
            return _returnnext(pkb, ethernetInput);
        }
        int r1 = vxlan.getReserved1();
        int r2 = vxlan.getReserved2();

        if (pkb.debugger.isDebugOn()) {
            pkb.debugger
                .append("r1=").append(r1)
                .append(", r2=").append(r2)
                .newLine();
        }

        if (r2 > 250) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "possible loop detected from " + pkb.devin + " with packet " + vxlan);

            final int I_DETECTED_A_POSSIBLE_LOOP = Consts.I_DETECTED_A_POSSIBLE_LOOP;
            final int I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN = Consts.I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN;

            boolean possibleLoop = (r1 & I_DETECTED_A_POSSIBLE_LOOP) == I_DETECTED_A_POSSIBLE_LOOP;
            boolean willDisconnect = (r1 & I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN) == I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN;

            if (possibleLoop && willDisconnect) {
                Logger.error(LogType.INVALID_EXTERNAL_DATA, "disconnect from " + pkb.devin + " due to possible loop");
                pkb.network.macTable.disconnect(pkb.devin);

                if (pkb.debugger.isDebugOn()) {
                    pkb.debugger
                        .append("possible loop from ").append(pkb.devin.name())
                        .newLine();
                }
                return HandleResult.DROP;
            }
            if (!possibleLoop && !willDisconnect) {
                vxlan.setReserved1(r1 | I_DETECTED_A_POSSIBLE_LOOP);
            } else {
                vxlan.setReserved1(r1 | I_DETECTED_A_POSSIBLE_LOOP | I_WILL_DISCONNECT_FROM_YOU_IF_I_RECEIVE_AGAIN);
            }
        }
        vxlan.setReserved2(r2 + 1);

        return _returnnext(pkb, ethernetInput);
    }
}
