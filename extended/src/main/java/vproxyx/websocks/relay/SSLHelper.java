package vproxyx.websocks.relay;

import tlschannel.impl.TlsExplorer;
import vproxybase.connection.Connection;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.RingBuffer;
import vproxybase.util.nio.ByteArrayChannel;
import vproxybase.util.ringbuffer.SSLUtils;
import vproxybase.util.ringbuffer.ssl.SSL;
import vproxybase.util.ringbuffer.ssl.SSLEngineBuilder;
import vproxybase.util.ringbuffer.ssl.VSSLContext;
import vproxyx.websocks.WebSocksUtils;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SSLHelper {
    private SSLHelper() {
    }

    // return error message
    public static IOException extractSniFromClientHello(RingBuffer inBuffer, String[] sni) {
        // prepare a byte buffer
        ByteBuffer clientHello = ByteBuffer.wrap(inBuffer.getBytes());
        // retrieve SNI
        {
            SNIServerName sniServerName;
            try {
                sniServerName = TlsExplorer.explore(clientHello).get(StandardConstants.SNI_HOST_NAME);
            } catch (Throwable t) {
                String msg = "decoding CLIENT_HELLO in TlsExplorer failed";
                Logger.error(LogType.INVALID_EXTERNAL_DATA, msg, t);
                return new IOException(msg, t);
            }
            if (sniServerName instanceof SNIHostName) {
                sni[0] = ((SNIHostName) sniServerName).getAsciiName();
            }
        }

        if (sni[0] == null) {
            String msg = "SNI not provided, cannot determine which host to connect to";
            Logger.error(LogType.INVALID_EXTERNAL_DATA, msg);
            return new IOException(msg);
        }

        return null;
    }

    public static IOException extractAlpnFromClientHello(RingBuffer inBuffer, String[][] alpnHolder) {
        // prepare a byte buffer
        ByteBuffer clientHello = ByteBuffer.wrap(inBuffer.getBytes());
        // retrieve ALPN
        {
            SSLContext sslContext = WebSocksUtils.getHttpsSniErasureSSLContext().sslContextHolder.choose("www.example.com"); // get any of them
            if (sslContext == null) {
                String msg = "no cert/key provided";
                Logger.error(LogType.IMPROPER_USE, msg);
                return new IOException(msg);
            }
            ByteBuffer dst = ByteBuffer.allocate(4096);
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
            engine.setHandshakeApplicationProtocolSelector((e, ls) -> {
                String[] arr = new String[ls.size()];
                arr = ls.toArray(arr);
                alpnHolder[0] = arr;
                return "";
            });
            SSLEngineResult res;
            try {
                res = engine.unwrap(clientHello, dst);
            } catch (SSLException e) {
                String msg = "parsing CLIENT_HELLO in SSLEngine failed";
                Logger.error(LogType.INVALID_EXTERNAL_DATA, msg, e);
                return new IOException(msg, e);
            }
            if (res.getStatus() != SSLEngineResult.Status.OK) {
                String msg = "handling CLIENT_HELLO in SSLEngine failed: " + res.getStatus();
                Logger.error(LogType.INVALID_EXTERNAL_DATA, msg);
                return new IOException(msg);
            }
            if (res.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                // this might block the event loop, but as I tested, the time for calling this method is very short
                // so it won't be too much trouble for blocking the thread while there are only few mega bytes per second
                // as client side
                engine.getDelegatedTask().run();
            }
            // now we should be able to do wrap
            try {
                engine.wrap(clientHello, dst);
            } catch (SSLException e) {
                String msg = "wrapping SERVER_HELLO failed";
                Logger.error(LogType.SSL_ERROR, msg, e);
                return new IOException(msg, e);
            }
            // now the alpn should have been retrieved
        }
        return null;
    }

    public static void replaceToSSLBuffersToAConnectionJustReceivedClientHello(Connection accepted, String alpn) {
        // consume all data from the accepted connection with raw simple ring buffers
        RingBuffer oldIn = accepted.getInBuffer();
        RingBuffer oldOut = accepted.getOutBuffer();
        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(oldIn.used());
        oldIn.writeTo(chnl);

        // construct and replace buffers
        VSSLContext ctx = WebSocksUtils.getHttpsSniErasureSSLContext();
        SSL ssl = ctx.createSSL();
        SSLEngineBuilder builder = ssl.sslEngineBuilder;
        builder.configure(engine -> engine.setUseClientMode(false)); // is server
        if (alpn != null) {
            builder.configure(engine -> engine.setHandshakeApplicationProtocolSelector((e, as) -> alpn));
        }
        SSLUtils.SSLBufferPair pair = SSLUtils.genbufForServer(ssl, RingBuffer.allocate(24576), RingBuffer.allocate(24576), accepted.channel);
        try {
            accepted.UNSAFE_replaceBuffer(pair.left, pair.right);
        } catch (IOException e) {
            Logger.shouldNotHappen("replaceBuffers when they are empty, should not throw exception");
            throw new RuntimeException(e);
        }

        // write data back into the buffers
        RingBuffer newIn = accepted.getInBuffer();
        newIn.storeBytesFrom(chnl);

        // now the oldIn and oldOut are no longer used
        oldIn.clean();
        oldOut.clean();
    }
}
