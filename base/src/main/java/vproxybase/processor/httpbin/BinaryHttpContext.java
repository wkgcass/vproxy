package vproxybase.processor.httpbin;

import vfd.IPPort;
import vproxybase.processor.Hint;
import vproxybase.processor.OOContext;
import vproxybase.processor.Processor;
import vproxybase.processor.httpbin.frame.SettingsFrame;
import vproxybase.util.Logger;

public class BinaryHttpContext extends OOContext<BinaryHttpSubContext> {
    final IPPort clientAddress;
    BinaryHttpSubContext frontend; // considered not null

    // proxy settings
    Stream currentProxyTarget;
    Hint currentHint = null;
    // set $willUpgradeConnection on request
    boolean willUpgradeConnection = false;
    // set $upgradedConnection when response returned
    boolean upgradedConnection = false;

    public BinaryHttpContext(IPPort clientAddress) {
        this.clientAddress = clientAddress;
    }

    Processor.ConnectionTODO connection() {
        Processor.ConnectionTODO ret = Processor.ConnectionTODO.create();
        if (currentProxyTarget == null) {
            // choose one
            ret.connId = -1;
            ret.hint = connectionHint();
            ret.chosen = this::chosen;
        } else {
            ret.connId = currentProxyTarget.ctx.connId;
        }
        return ret;
    }

    public Hint connectionHint() {
        Hint currentHint = this.currentHint;
        this.currentHint = null;
        return currentHint;
    }

    private void chosen(Processor.SubContext subCtx) {
        if (frontendSubCtx.currentPendingStream == null) {
            Logger.shouldNotHappen("front.lastPendingStream is null while chosen() is called");
            return;
        }
        Stream frontendStream = frontendSubCtx.currentPendingStream;
        frontendSubCtx.currentPendingStream = null;

        // need to create a client Stream to the backend
        Stream backendStream = ((BinaryHttpSubContextCaster) subCtx).castToBinaryHttpSubContext()
            .streamHolder.createClientStream(SettingsFrame.DEFAULT_WINDOW_SIZE, SettingsFrame.DEFAULT_WINDOW_SIZE);
        // create session
        new StreamSession(frontendStream, backendStream);
    }
}
