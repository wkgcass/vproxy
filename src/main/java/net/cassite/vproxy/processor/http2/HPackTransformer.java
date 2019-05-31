package net.cassite.vproxy.processor.http2;

import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import net.cassite.vproxy.util.ArrayInputStream;
import net.cassite.vproxy.util.ArrayOutputStream;
import net.cassite.vproxy.util.ByteArray;

import java.io.IOException;

class HPackTransformer {
    private final int BUFFER_SIZE = 65536; // make the buffer big enough for almost all cases

    private final Decoder decoder;
    private final SyncOutputListener lsn;
    private final ArrayOutputStream outBuffer = ArrayOutputStream.to(ByteArray.from(new byte[BUFFER_SIZE]));

    HPackTransformer(int maxHeaderTableSize) {
        this.decoder = new Decoder(BUFFER_SIZE, maxHeaderTableSize);
        Encoder encoder = new Encoder(0);
        this.lsn = new SyncOutputListener(encoder, outBuffer);
    }

    ByteArray transform(ByteArray array) throws IOException {
        decoder.decode(ArrayInputStream.from(array), lsn);
        return outBuffer.get();
    }

    public void endHeaders() {
        decoder.endHeaderBlock();
    }
}
