package vproxybase.util.ringbuffer;

import vproxybase.util.Logger;
import vproxybase.util.RingBuffer;
import vproxybase.util.RingBufferETHandler;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class ProxyOutputRingBuffer extends AbstractRingBuffer {
    private class DefaultBufferETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            triggerReadable();
        }

        @Override
        public void writableET() {
            // dose not care, because this buffer is only used as output buffer
        }
    }

    private class ProxiedETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            if (isProxy) {
                triggerReadable();
            }
        }

        @Override
        public void writableET() {
            // does not care, because this buffer is only used as output buffer
        }
    }

    public interface ProxyDoneCallback {
        void proxyDone();
    }

    private final ProxiedETHandler proxiedETHandler = new ProxiedETHandler();

    private boolean isProxy = false; // true = write data from attached, false = write data from

    private final SimpleRingBuffer defaultBuffer;
    private final int cap;

    private RingBuffer proxied;
    private int proxyLen;
    private ProxyDoneCallback proxyDoneCallback;

    private ProxyOutputRingBuffer(SimpleRingBuffer defaultBuffer) {
        this.defaultBuffer = defaultBuffer;
        this.cap = defaultBuffer.capacity();
        defaultBuffer.addHandler(new DefaultBufferETHandler());
    }

    public static ProxyOutputRingBuffer allocateDirect(int cap) {
        return new ProxyOutputRingBuffer(SimpleRingBuffer.allocateDirect(cap));
    }

    public void proxy(RingBuffer proxied, int proxyLen, ProxyDoneCallback cb) {
        if (this.proxied != null)
            throw new IllegalStateException("has a proxied buffer, with proxyLen = " + proxyLen);
        assert Logger.lowLevelDebug("get a buffer to proxy, data length is " + proxyLen);
        this.proxied = proxied;
        this.proxyLen = proxyLen;
        this.proxyDoneCallback = cb;
        this.proxied.addHandler(proxiedETHandler);
        if (defaultBuffer.used() == 0) {
            assert Logger.lowLevelDebug("the defaultBuffer is empty now, switch to proxy mode");
            isProxy = true;
            triggerReadable();
        } else {
            assert Logger.lowLevelDebug("still have data in the defaultBuffer");
        }
    }

    public void newDataFromProxiedBuffer() {
        if (proxied == null)
            throw new IllegalStateException("no buffer to proxy but alarmed with 'new data from proxied buffer'");
        if (isProxy) {
            triggerReadable();
        }
    }

    @Override
    public int storeBytesFrom(ReadableByteChannel channel) throws IOException {
        if (proxied != null)
            throw new IllegalStateException("has a proxied buffer, with proxyLen = " + proxyLen);
        return defaultBuffer.storeBytesFrom(channel);
    }

    @Override
    public int writeTo(WritableByteChannel channel, int maxBytesToWrite) throws IOException {
        if (isProxy) {
            int toWrite = Math.min(maxBytesToWrite, proxyLen);
            int wrote = proxied.writeTo(channel, toWrite);
            proxyLen -= wrote;
            if (proxyLen == 0) {
                isProxy = false;
                proxied.removeHandler(proxiedETHandler);
                proxied = null;
                ProxyDoneCallback cb = proxyDoneCallback;
                proxyDoneCallback = null;
                assert Logger.lowLevelDebug("proxy end, calling proxy done callback");
                cb.proxyDone();
            }
            return wrote;
        } else {
            int wrote = defaultBuffer.writeTo(channel, maxBytesToWrite);
            if (wrote == maxBytesToWrite)
                return wrote;
            if (proxied == null)
                return wrote;
            assert Logger.lowLevelDebug("wrote all data from defaultBuffer, switch to proxy mode");
            isProxy = true;
            return wrote + writeTo(channel, maxBytesToWrite - wrote);
        }
    }

    @Override
    public int free() {
        return cap - used();
    }

    @Override
    public int used() {
        int proxyPart = 0;
        if (proxied != null) {
            int ret = cap;
            if (ret > proxyLen) ret = proxyLen;
            int foo = proxied.used();
            if (ret > foo) ret = foo;
            return ret; // the minimum of cap, proxyLen, and proxied.used()
        }
        if (isProxy) {
            return proxyPart;
        } else {
            return Math.min(cap, defaultBuffer.used() + proxyPart);
        }
    }

    @Override
    public int capacity() {
        return cap;
    }

    @Override
    public void clean() {
        if (proxied != null) {
            proxied.removeHandler(proxiedETHandler);
            proxied = null;
            proxyLen = 0;
            proxyDoneCallback = null;
        }
        defaultBuffer.clean();
    }

    @Override
    public void clear() {
        defaultBuffer.clear();
    }
}
