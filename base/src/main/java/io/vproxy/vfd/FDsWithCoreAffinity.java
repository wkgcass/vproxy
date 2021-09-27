package io.vproxy.vfd;

import java.io.IOException;

public interface FDsWithCoreAffinity {
    void setCoreAffinity(long mask) throws IOException;
}
