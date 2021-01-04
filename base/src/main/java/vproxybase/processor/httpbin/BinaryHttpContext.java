package vproxybase.processor.httpbin;

import vfd.IPPort;
import vproxybase.processor.Hint;
import vproxybase.processor.OOContext;
import vproxybase.processor.httpbin.frame.SettingsFrame;
import vproxybase.util.Logger;

public class BinaryHttpContext extends OOContext<BinaryHttpSubContext> {
    final IPPort clientAddress;
    BinaryHttpSubContext frontend; // considered not null

    // proxy settings
    Stream currentProxyTarget;
    Hint currentHint = null;

    public BinaryHttpContext(IPPort clientAddress) {
        this.clientAddress = clientAddress;
    }

    @Override
    public int connection(BinaryHttpSubContext front) {
        if (currentProxyTarget == null) {
            return -1; // choose one
        } else {
            return currentProxyTarget.ctx.connId;
        }
    }

    @Override
    public Hint connectionHint(BinaryHttpSubContext front) {
        Hint currentHint = this.currentHint;
        this.currentHint = null;
        return currentHint;
    }

    @Override
    public void chosen(BinaryHttpSubContext front, BinaryHttpSubContext subCtx) {
        if (front.currentPendingStream == null) {
            Logger.shouldNotHappen("front.lastPendingStream is null while chosen() is called");
            return;
        }
        Stream frontendStream = front.currentPendingStream;
        front.currentPendingStream = null;

        // need to create a client Stream to the backend
        Stream backendStream = subCtx.streamHolder.createClientStream(SettingsFrame.DEFAULT_WINDOW_SIZE, SettingsFrame.DEFAULT_WINDOW_SIZE);
        // create session
        new StreamSession(frontendStream, backendStream);
    }
}
