package io.vproxy.base.processor;

import io.vproxy.base.util.anno.ThreadSafe;
import io.vproxy.vfd.IPPort;

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
