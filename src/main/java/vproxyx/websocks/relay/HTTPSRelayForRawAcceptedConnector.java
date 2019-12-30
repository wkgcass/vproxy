package vproxyx.websocks.relay;

import vproxy.connection.ConnectableConnection;
import vproxy.connection.Connection;
import vproxy.connection.NetEventLoop;
import vproxy.util.RingBuffer;
import vproxy.util.nio.ByteArrayChannel;
import vproxy.util.ringbuffer.SSLUtils;
import vproxy.util.ringbuffer.ssl.SSL;
import vproxy.util.ringbuffer.ssl.SSLEngineBuilder;
import vproxy.util.ringbuffer.ssl.VSSLContext;
import vproxyx.websocks.AlreadyConnectedConnector;
import vproxyx.websocks.WebSocksUtils;

import java.io.IOException;
import java.net.InetSocketAddress;

public class HTTPSRelayForRawAcceptedConnector extends AlreadyConnectedConnector {
    private boolean useH2;

    public HTTPSRelayForRawAcceptedConnector(InetSocketAddress remote, ConnectableConnection conn, NetEventLoop loop, boolean useH2) {
        super(remote, conn, loop);
        this.useH2 = useH2;
    }

    @Override
    public void beforeConnect(Connection accepted) throws IOException {
        // consume all data from the accepted connection with raw simple ring buffers
        RingBuffer oldIn = accepted.getInBuffer();
        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(oldIn.used());
        oldIn.writeTo(chnl);

        // construct and replace buffers
        VSSLContext ctx = WebSocksUtils.getHTTPSRelaySSLContext();
        SSL ssl = ctx.createSSL();
        SSLEngineBuilder builder = ssl.sslEngineBuilder;
        builder.configure((engine, p, s) -> engine.setUseClientMode(false)); // is server
        builder.configure((engine, p, s) -> engine.setHandshakeApplicationProtocolSelector((e, l) -> useH2 ? "h2" : "http/1.1"));
        SSLUtils.SSLBufferPair pair = SSLUtils.genbufForServer(ssl, RingBuffer.allocate(24576), RingBuffer.allocate(24576));
        accepted.UNSAFE_replaceBuffer(pair.left, pair.right);

        // write data back into the buffers
        RingBuffer newIn = accepted.getInBuffer();
        newIn.storeBytesFrom(chnl);
    }
}
