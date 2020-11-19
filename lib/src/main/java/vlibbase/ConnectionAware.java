package vlibbase;

import java.io.IOException;

public interface ConnectionAware<ConnType> {
    // this method should only be called by ConnRef
    ConnType receiveTransferredConnection0(ConnRef conn) throws IOException;
}
