package net.cassite.vproxy.processor.http2;

import com.twitter.hpack.Encoder;
import com.twitter.hpack.HeaderListener;
import net.cassite.vproxy.util.Logger;

import java.io.IOException;
import java.io.OutputStream;

public class SyncOutputListener implements HeaderListener {
    private final Encoder encoder;
    private final OutputStream out;
    private final Header[] headers;
    private final boolean[] added; // whether it's already added into the header list

    public SyncOutputListener(Encoder encoder, OutputStream out, Header[] headers) {
        this.encoder = encoder;
        this.out = out;
        this.headers = headers;
        this.added = new boolean[headers == null ? 0 : headers.length];
    }

    @Override
    public void addHeader(byte[] name, byte[] value, boolean sensitive) throws IOException {
        byte[] replaced = checkAndGet(name, value);
        if (replaced != null) {
            value = replaced;
        }
        encoder.encodeHeader(out, name, value, sensitive);
    }

    private byte[] checkAndGet(byte[] key, byte[] value) {
        if (headers == null || headers.length == 0) {
            return null;
        }
        for (int i = 0; i < headers.length; i++) {
            Header h = headers[i];
            if (h.key.length != key.length) {
                continue;
            }
            if (h.keyStr.equals(new String(key).toLowerCase())) {
                // header keys are the same
                assert Logger.lowLevelDebug("replacing header " +
                    "new header: " + h.keyStr + ": " + new String(h.value) + " " +
                    "old header: " + new String(key) + ": " + new String(value));
                added[i] = true;
                return h.value;
            }
        }
        // not found
        return null;
    }

    void addHeaders() throws IOException {
        if (headers == null)
            return;
        for (int i = 0; i < headers.length; i++) {
            Header h = headers[i];
            if (added[i])
                continue;
            assert Logger.lowLevelDebug("adding header " +
                "new header: " + h.keyStr + ": " + new String(h.value));
            added[i] = true;
            encoder.encodeHeader(out, h.key, h.value, false);
        }
    }

    void endHeaders() {
        // set everything to not added (added[n]=false)
        for (int i = 0; i < added.length; ++i) {
            added[i] = false;
        }
    }
}
