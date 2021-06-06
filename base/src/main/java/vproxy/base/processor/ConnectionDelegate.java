package vproxy.base.processor;

import vproxy.base.util.ThreadSafe;
import vproxy.vfd.IPPort;

public abstract class ConnectionDelegate {
    /**
     * associated address, frontend address for frontend, backend address for backend
     */
    public final IPPort associatedAddress;

    public ConnectionDelegate(IPPort associatedAddress) {
        this.associatedAddress = associatedAddress;
    }

    /**
     * pause the data processing of the connection
     */
    @ThreadSafe
    public abstract void pause();

    /**
     * resume the data processing of the connection
     */
    @ThreadSafe
    public abstract void resume();
}
