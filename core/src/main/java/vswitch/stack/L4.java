package vswitch.stack;

import vproxybase.util.Logger;

public class L4 {
    private final L3 L3;

    public L4(L3 l3) {
        L3 = l3;
    }

    public void input(InputPacketL4Context ctx) {
        assert Logger.lowLevelDebug("L4.input(" + ctx + ")");
        if (!wantToHandle(ctx)) {
            assert Logger.lowLevelDebug("L4 stack doesn't handle this packet");
            return;
        }
        // implement more L4 protocols in the future
        assert Logger.lowLevelDebug(ctx.handlingUUID + " this packet is not handled by L4");
    }

    public boolean wantToHandle(InputPacketL3Context l3ctx) {
        // implement more L4 protocols in the future
        boolean result = false;
        assert Logger.lowLevelDebug("wantToHandle(" + l3ctx + ") = " + result);
        return result;
    }
}
