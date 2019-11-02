package vproxy.processor.http2;

import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import vproxy.util.ByteArray;
import vproxy.util.io.ArrayInputStream;
import vproxy.util.io.ArrayOutputStream;

import java.io.IOException;

class HPackTransformer {
    private final int BUFFER_SIZE = 65536; // make the buffer big enough for almost all cases

    private final Decoder decoder;
    private final SyncOutputListener lsn;
    private final ArrayOutputStream outBuffer = ArrayOutputStream.to(ByteArray.from(new byte[BUFFER_SIZE]));

    HPackTransformer(int maxHeaderTableSize, Header[] headers) {
        this.decoder = new Decoder(BUFFER_SIZE, maxHeaderTableSize);
        Encoder encoder = new Encoder(0);
        this.lsn = new SyncOutputListener(encoder, outBuffer, headers);
    }

    ByteArray transform(ByteArray array, boolean addHeaders) throws IOException {
        decoder.decode(ArrayInputStream.from(array), lsn);
        if (addHeaders) {
            lsn.addHeaders();
        }
        return outBuffer.get();
    }

    public void endHeaders() {
        lsn.endHeaders();
        decoder.endHeaderBlock();
    }
}
