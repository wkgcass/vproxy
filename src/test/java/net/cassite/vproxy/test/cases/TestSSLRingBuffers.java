package net.cassite.vproxy.test.cases;

import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.RingBufferETHandler;
import net.cassite.vproxy.util.ringbuffer.SSLUnwrapRingBuffer;
import net.cassite.vproxy.util.ringbuffer.SSLWrapRingBuffer;
import net.cassite.vproxy.util.ringbuffer.SimpleRingBuffer;
import org.junit.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertEquals;

public class TestSSLRingBuffers {
    private static final String keyStoreFile = "testkeys";
    private static final String trustStoreFile = "testkeys";

    ConcurrentLinkedQueue<Runnable> q = new ConcurrentLinkedQueue<>();

    SimpleRingBuffer serverOutputData = RingBuffer.allocate(16384);
    SimpleRingBuffer serverInputData = RingBuffer.allocate(16384);
    SSLWrapRingBuffer serverWrap;
    SSLUnwrapRingBuffer serverUnwrap;

    SimpleRingBuffer clientOutputData = RingBuffer.allocate(16384);
    SimpleRingBuffer clientInputData = RingBuffer.allocate(16384);
    SSLWrapRingBuffer clientWrap;
    SSLUnwrapRingBuffer clientUnwrap;

    byte[] tmp = new byte[16384];
    ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(tmp);

    int serverTotalData;
    int clientTotalData;

    @Test
    public void wrapThenUnwrap() throws Exception {
        // System.setProperty("javax.net.debug", "all");

        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = "passphrase".toCharArray();

        ks.load(TestSSLRingBuffers.class.getResourceAsStream("/" + keyStoreFile), passphrase);
        ts.load(TestSSLRingBuffers.class.getResourceAsStream("/" + trustStoreFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLEngine serverEngine = context.createSSLEngine();
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

        SSLEngine clientEngine = context.createSSLEngine("cassite.net", 443);
        clientEngine.setUseClientMode(true);

        // init buffers

        serverWrap = new SSLWrapRingBuffer(serverOutputData, serverEngine, q::add);
        serverUnwrap = new SSLUnwrapRingBuffer(serverInputData, serverEngine, q::add, serverWrap);

        clientWrap = new SSLWrapRingBuffer(clientOutputData, clientEngine, q::add);
        clientUnwrap = new SSLUnwrapRingBuffer(clientInputData, clientEngine, q::add, clientWrap);

        // set callback
        serverWrap.addHandler(new RingBufferETHandler() {
            @Override
            public void readableET() {
                serverSendMessage();
            }

            @Override
            public void writableET() {
                // will not fire
            }
        });
        clientWrap.addHandler(new RingBufferETHandler() {
            @Override
            public void readableET() {
                clientSendMessage();
            }

            @Override
            public void writableET() {
                // will not fire
            }
        });

        // set data

        String serverMsg = "Hello Client, I'm Server";
        String clientMsg = "Hi Server, I'm Client";
        serverOutputData.storeBytesFrom(ByteArrayChannel.fromFull(serverMsg.getBytes()));
        clientOutputData.storeBytesFrom(ByteArrayChannel.fromFull(clientMsg.getBytes()));
        serverTotalData = serverOutputData.used();
        clientTotalData = clientOutputData.used();

        // initial push
        clientSendMessage();

        do {
            Thread.sleep(1);
            runQ();
        } while (serverInputData.used() != clientTotalData || clientInputData.used() != serverTotalData);

        assertEquals(clientMsg, serverInputData.toString());
        assertEquals(serverMsg, clientInputData.toString());
    }

    void runQ() {
        Runnable r;
        while ((r = q.poll()) != null) {
            r.run();
        }
    }

    void serverSendMessage() {
        chnl.reset();
        int writeBytes = serverWrap.writeTo(chnl);
        System.out.println("================");
        System.out.println("server sends: " + writeBytes);
        System.out.println("================");

        // write to client
        int storeBytes = clientUnwrap.storeBytesFrom(chnl);
        System.out.println("================");
        System.out.println("client reads: " + storeBytes);
        System.out.println("================");
    }

    private void clientSendMessage() {
        chnl.reset();
        int writeBytes = clientWrap.writeTo(chnl);
        System.out.println("================");
        System.out.println("client sends: " + writeBytes);
        System.out.println("================");

        // write to server
        int storeBytes = serverUnwrap.storeBytesFrom(chnl);
        System.out.println("================");
        System.out.println("server reads: " + storeBytes);
        System.out.println("================");
    }
}
