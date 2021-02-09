package vproxybase.processor;

import vfd.IPPort;
import vproxybase.util.ThreadSafe;

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
