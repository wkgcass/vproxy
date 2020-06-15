package vproxybase.processor.http2;

import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import vproxybase.util.ByteArray;
import vproxybase.util.io.ArrayInputStream;
import vproxybase.util.io.ArrayOutputStream;

import java.io.IOException;
import java.util.function.Consumer;

class HPackTransformer {
    private final int BUFFER_SIZE = 65536; // make the buffer big enough for almost all cases

    private final Decoder decoder;
    private final SyncOutputListener lsn;
    private final ArrayOutputStream outBuffer = ArrayOutputStream.to(ByteArray.from(new byte[BUFFER_SIZE]));

    HPackTransformer(int maxHeaderTableSize,
                     Header[] additionalHeaders,
                     Consumer<String> uriListener,
                     Consumer<String> hostHeaderListener) {
        this.decoder = new Decoder(BUFFER_SIZE, maxHeaderTableSize);
        Encoder encoder = new Encoder(0);
        this.lsn = new SyncOutputListener(encoder, outBuffer, additionalHeaders, uriListener, hostHeaderListener);
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
