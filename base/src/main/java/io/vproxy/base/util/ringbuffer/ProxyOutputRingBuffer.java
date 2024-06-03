package io.vproxy.base.util.ringbuffer;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.RingBufferETHandler;
import io.vproxy.vfd.ReadableByteStream;
import io.vproxy.vfd.WritableByteStream;

import java.io.IOException;

public class ProxyOutputRingBuffer extends AbstractRingBuffer {
    private class DefaultBufferETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            triggerReadable();
        }

        @Override
        public void writableET() {
            var proxyHandle = ProxyOutputRingBuffer.this.proxyHandle;
            if (proxyHandle == null) {
                triggerWritable();
            }
        }
    }

    private class ProxyETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            var proxyHandle = ProxyOutputRingBuffer.this.proxyHandle;
            if (proxyHandle != null && proxyHandle.enabled) {
                triggerReadable();
            }
        }

        @Override
        public void writableET() {
            // the ProxyOutputRingBuffer will not be writable until proxy is done
        }
    }

    private final ProxyETHandler proxyETHandler = new ProxyETHandler();

    protected final SimpleRingBuffer defaultBuffer;
    private final int cap;

    public interface ProxyDoneCallback {
        void proxyDone();
    }

    protected class ProxyInfo {
        public boolean enabled = false;
        // true = write data from rb, false = write data from defaultBuffer

        public final RingBuffer rb;
        public int len;
        public final ProxyDoneCallback cb;

        public ProxyInfo(RingBuffer rb, int len, ProxyDoneCallback cb) {
            this.rb = rb;
            this.len = len;
            this.cb = cb;
            rb.addHandler(proxyETHandler);
        }

        public void proxyDone() {
            proxyDone(true);
        }

        public void proxyDone(boolean triggerEvents) {
            rb.removeHandler(proxyETHandler);
            if (triggerEvents) {
                triggerWritable();
                assert Logger.lowLevelDebug("proxy end, calling proxy done callback");
                cb.proxyDone();
            }
        }
    }

    protected ProxyInfo proxyHandle = null;

    protected ProxyOutputRingBuffer(SimpleRingBuffer defaultBuffer) {
        this.defaultBuffer = defaultBuffer;
        this.cap = defaultBuffer.capacity();
        defaultBuffer.addHandler(new DefaultBufferETHandler());
    }

    public static ProxyOutputRingBuffer allocateDirect(int cap) {
        return new ProxyOutputRingBuffer(SimpleRingBuffer.allocateDirect(cap));
    }

    public void proxy(RingBuffer proxyBuffer, int proxyLen, ProxyDoneCallback cb) {
        if (proxyHandle != null)
            throw new IllegalStateException("already has proxyHandle, with proxyLen = " + proxyHandle.len);
        assert Logger.lowLevelDebug("get a buffer to proxy, data length is " + proxyLen);
        this.proxyHandle = new ProxyInfo(proxyBuffer, proxyLen, cb);
        if (defaultBuffer.used() == 0) {
            assert Logger.lowLevelDebug("the defaultBuffer is empty now, switch to proxy mode");
            proxyHandle.enabled = true;
            triggerReadable();
        } else {
            assert Logger.lowLevelDebug("still have data in the defaultBuffer");
        }
    }

    public void newDataFromProxyRingBuffer() {
        if (proxyHandle == null)
            throw new IllegalStateException("no buffer to proxy but alarmed with 'new data from proxied buffer'");
        if (proxyHandle.enabled) {
            triggerReadable();
        }
    }

    @Override
    public int storeBytesFrom(ReadableByteStream channel) throws IOException {
        if (proxyHandle != null)
            throw new IllegalStateException("already has proxyHandle, with proxyLen = " + proxyHandle.len);
        return defaultBuffer.storeBytesFrom(channel);
    }

    @Override
    public int writeTo(WritableByteStream channel, int maxBytesToWrite) throws IOException {
        if (proxyHandle != null && proxyHandle.enabled) {
            int toWrite = Math.min(maxBytesToWrite, proxyHandle.len);
            int wrote = proxyHandle.rb.writeTo(channel, toWrite);
            proxyHandle.len -= wrote;
            if (proxyHandle.len == 0) {
                proxyHandle.proxyDone();
                proxyHandle = null;
            }
            return wrote;
        } else {
            int wrote = defaultBuffer.writeTo(channel, maxBytesToWrite);
            if (defaultBuffer.used() == 0 && proxyHandle != null) {
                assert Logger.lowLevelDebug("wrote all data from defaultBuffer, switch to proxy mode");
                proxyHandle.enabled = true;
            }
            if (wrote == maxBytesToWrite || proxyHandle == null)
                return wrote;
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
        if (proxyHandle != null) {
            var proxyBufferUsed = proxyHandle.rb.used();
            proxyPart = Math.min(proxyBufferUsed, proxyHandle.len);
        }
        return Math.min(cap, defaultBuffer.used() + proxyPart);
    }

    @Override
    public int capacity() {
        return cap;
    }

    @Override
    public void clean() {
        if (proxyHandle != null) {
            proxyHandle.proxyDone(false);
            proxyHandle = null;
        }
        defaultBuffer.clean();
    }

    @Override
    public void clear() {
        defaultBuffer.clear();
    }
}
