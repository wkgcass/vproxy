package io.vproxy.vmirror;

import java.io.IOException;
import java.io.OutputStream;

public class MirrorConfig {
    public String outputFilePath;
    public OutputStream output;

    public MirrorConfig() {
    }

    public void destroy() {
        if (output != null) {
            try {
                output.close();
            } catch (IOException ignore) {
            }
        }
    }
}
