package vproxy.vfd;

import java.io.IOException;

public interface FDsWithPoll {
    FDSelector openSelector(boolean preferPoll) throws IOException;
}
