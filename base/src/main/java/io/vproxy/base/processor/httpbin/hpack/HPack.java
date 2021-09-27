package io.vproxy.base.processor.httpbin.hpack;

import io.vproxy.base.processor.httpbin.entity.Header;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.dep.com.twitter.hpack.hpack.Decoder;
import io.vproxy.dep.com.twitter.hpack.hpack.Encoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class HPack {
    private final Decoder decoder;
    private final Encoder encoder;
    private final SyncHeaderListener sync = new SyncHeaderListener();

    public HPack(int decoderMaxHeaderTableSize, int encoderMaxHeaderTableSize) {
        this.decoder = new Decoder(65536, decoderMaxHeaderTableSize);
        this.encoder = new Encoder(encoderMaxHeaderTableSize);
    }

    public List<Header> decode(ByteArray headers) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(headers.toJavaArray());
        try {
            decoder.decode(bais, sync);
        } catch (IOException e) {
            Logger.error(LogType.INVALID_EXTERNAL_DATA, "hpack decode failed", e);
            throw e;
        }
        return sync.getHeaders();
    }

    public void setDecoderMaxHeaderTableSize(int size) {
        decoder.setMaxHeaderTableSize(size);
    }

    public ByteArray encode(List<Header> headers) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Header h : headers) {
            try {
                encoder.encodeHeader(baos, h.key, h.value, h.caseSensitive);
            } catch (IOException e) {
                Logger.shouldNotHappen("hpack encode failed", e);
                throw new RuntimeException(e);
            }
        }
        return ByteArray.from(baos.toByteArray());
    }

    private final ByteArrayOutputStream fooBAOS = new ByteArrayOutputStream();

    public void setEncoderMaxHeaderTableSize(int size) {
        try {
            encoder.setMaxHeaderTableSize(fooBAOS, size);
        } catch (IOException e) {
            Logger.shouldNotHappen("hpack setting encoder max header table size failed", e);
        }
        fooBAOS.reset();
    }
}
