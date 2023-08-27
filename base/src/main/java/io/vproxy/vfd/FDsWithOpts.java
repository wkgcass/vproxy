package io.vproxy.vfd;

import java.io.IOException;

public interface FDsWithOpts {
    FDSelector openSelector(Options opts) throws IOException;

    record Options(boolean preferPoll, int epfd) {
        public static Options defaultValue() {
            return new Options(false, 0);
        }
    }
}
