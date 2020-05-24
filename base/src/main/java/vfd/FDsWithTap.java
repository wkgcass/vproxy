package vfd;

import java.io.IOException;

public interface FDsWithTap {
    TapDatagramFD openTap(String devPattern) throws IOException;

    boolean tapNonBlockingSupported() throws IOException;
}
