package net.cassite.vproxy.processor.http2;

import com.twitter.hpack.Encoder;
import com.twitter.hpack.HeaderListener;

import java.io.IOException;
import java.io.OutputStream;

public class SyncOutputListener implements HeaderListener {
    private final Encoder encoder;
    private final OutputStream out;

    public SyncOutputListener(Encoder encoder, OutputStream out) {
        this.encoder = encoder;
        this.out = out;
    }

    @Override
    public void addHeader(byte[] name, byte[] value, boolean sensitive) throws IOException {
        encoder.encodeHeader(out, name, value, sensitive);
    }
}
