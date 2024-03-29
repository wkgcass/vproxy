package io.vproxy.poc;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.AnnotationKeys;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.ByteArray;
import io.vproxy.msquic.*;
import io.vproxy.msquic.callback.ConnectionCallback;
import io.vproxy.msquic.callback.StreamCallback;
import io.vproxy.msquic.wrap.Configuration;
import io.vproxy.msquic.wrap.Connection;
import io.vproxy.msquic.wrap.Stream;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;
import io.vproxy.vfd.IPPort;

import java.util.Map;

import static io.vproxy.msquic.MsQuicConsts.*;

public class QuicPoc {
    public static void main(String[] args) throws Exception {
        var eventLoopGroup = new EventLoopGroup("quic", new Annotations(Map.of(
            AnnotationKeys.EventLoopGroup_UseMsQuic.name, "true"
        )));

        var reg = eventLoopGroup.getMsquicRegistration();
        Configuration conf;
        {
            var allocator = Allocator.ofUnsafe();
            var alpnBuffers = new QuicBuffer.Array(allocator, 2);
            alpnBuffers.get(0).setBuffer(new PNIString(allocator, "proto-x").MEMORY);
            alpnBuffers.get(0).setLength(7);
            var settings = new QuicSettings(allocator);
            {
                settings.getIsSet().setIdleTimeoutMs(true);
                settings.setIdleTimeoutMs(60_000);
                settings.getIsSet().setCongestionControlAlgorithm(true);
                settings.setCongestionControlAlgorithm((short) QUIC_CONGESTION_CONTROL_ALGORITHM_BBR);
                settings.getIsSet().setPeerBidiStreamCount(true);
                settings.setPeerBidiStreamCount((short) 128);
            }
            var c = reg.opts.registrationQ.openConfiguration(alpnBuffers, 1, settings, null, null, allocator);
            if (c == null) {
                throw new Exception("failed to create configuration");
            }
            conf = new Configuration(new Configuration.Options(reg, c, allocator));
            var cred = new QuicCredentialConfig(allocator);
            cred.setType(QUIC_CREDENTIAL_TYPE_NONE);
            int flags = QUIC_CREDENTIAL_FLAG_CLIENT;
            flags |= QUIC_CREDENTIAL_FLAG_NO_CERTIFICATE_VALIDATION;
            cred.setFlags(flags);
            cred.setAllowedCipherSuites(
                QUIC_ALLOWED_CIPHER_SUITE_AES_128_GCM_SHA256 |
                QUIC_ALLOWED_CIPHER_SUITE_AES_256_GCM_SHA384 |
                QUIC_ALLOWED_CIPHER_SUITE_CHACHA20_POLY1305_SHA256);
            var err = conf.opts.configurationQ.loadCredential(cred);
            if (err != 0) {
                throw new RuntimeException("failed to load credential");
            }
        }
        System.out.println("init conn");
        Connection conn;
        {
            var allocator = Allocator.ofUnsafe();
            conn = new Connection(new Connection.Options(reg, allocator, new SampleConnectionCallback(), ref ->
                reg.opts.registrationQ.openConnection(MsQuicUpcall.connectionCallback, ref.MEMORY, null, allocator)));
            if (conn.connectionQ == null) {
                conn.close();
                throw new RuntimeException("failed to create connection");
            }
            int err = conn.start(conf, new IPPort("127.0.0.1", 443));
            if (err != 0) {
                conn.close();
                throw new RuntimeException("failed to start connection");
            }
        }
        System.out.println("connection started");
    }

    private static class SampleConnectionCallback implements ConnectionCallback {
        @Override
        public int connected(Connection conn, QuicConnectionEventConnected data) {
            var allocator = Allocator.ofUnsafe();
            var stream = new Stream(new Stream.Options(conn, allocator, new SampleStreamCallback(), ref ->
                conn.connectionQ.openStream(0, MsQuicUpcall.streamCallback, ref.MEMORY, null, allocator)));
            if (stream.streamQ == null) {
                System.out.println("failed to open stream");
                stream.close();
                return 1;
            }
            int err = stream.start(0);
            if (err != 0) {
                System.out.println("failed to start stream");
                stream.close();
                return err;
            }
            return 0;
        }
    }

    private static class SampleStreamCallback implements StreamCallback {
        @Override
        public int startComplete(Stream stream, QuicStreamEventStartComplete data) {
            var allocator = Allocator.ofUnsafe();
            var mem = new PNIString(allocator, "hello world");
            stream.send(allocator, mem.MEMORY);
            return 0;
        }

        @Override
        public int receive(Stream stream, QuicStreamEventReceive data) {
            int count = data.getBufferCount();
            var bufMem = data.getBuffers().MEMORY;
            bufMem = bufMem.reinterpret(QuicBuffer.LAYOUT.byteSize() * count);
            var bufs = new QuicBuffer.Array(bufMem);
            for (int i = 0; i < count; ++i) {
                var buf = bufs.get(i);
                var seg = buf.getBuffer().reinterpret(buf.getLength());
                System.out.println("Buffer[" + i + "]");
                System.out.println(ByteArray.from(seg).hexDump());
            }
            stream.close();
            return 0;
        }

        @Override
        public int shutdownComplete(Stream stream, QuicStreamEventShutdownComplete data) {
            stream.opts.connection.close();
            return 0;
        }
    }
}
