package io.vproxy.poc;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.AnnotationKeys;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.Utils;
import io.vproxy.msquic.*;
import io.vproxy.msquic.wrap.*;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIRef;
import io.vproxy.pni.PNIString;

import java.util.Map;
import java.util.function.Function;

import static io.vproxy.msquic.MsQuicConsts.*;

public class QuicPoc {
    public static void main(String[] args) throws Exception {
        var eventLoopGroup = new EventLoopGroup("quic", new Annotations(Map.of(
            AnnotationKeys.EventLoopGroup_UseMsQuic.name, "true"
        )));

        // at this point, quic should be ready

        System.out.println("init api");
        ApiTable api;
        {
            var allocator = Allocator.ofUnsafe();
            var table = MsQuic.get().open(QUIC_API_VERSION_2, null, allocator);
            if (table == null) {
                throw new Exception("failed to create api table");
            }
            api = new ApiTable(table, allocator);
        }
        System.out.println("init reg");
        Registration reg;
        {
            var allocator = Allocator.ofUnsafe();
            var conf = new QuicRegistrationConfigEx(allocator);
            var ref = PNIRef.of(eventLoopGroup);
            conf.setContext(ref.MEMORY);
            var r = api.apiTable.openRegistration(conf, null, allocator);
            if (r == null) {
                throw new Exception("failed to create registration");
            }
            ref.close(); // it will not be used anymore
            reg = new Registration(api.apiTable, r, allocator);
        }
        System.out.println("init conf");
        Configuration conf;
        {
            var allocator = Allocator.ofUnsafe();
            var alpnBuffers = new QuicBuffer.Array(allocator, 2);
            alpnBuffers.get(0).setBuffer(new PNIString(allocator, "proto-x").MEMORY);
            alpnBuffers.get(0).setLength(7);
            var settings = new QuicSettings(allocator);
            {
                settings.getIsSet().setIdleTimeoutMs(1);
                settings.setIdleTimeoutMs(60_000);
                settings.getIsSet().setCongestionControlAlgorithm(1);
                settings.setCongestionControlAlgorithm((short) QUIC_CONGESTION_CONTROL_ALGORITHM_BBR);
                settings.getIsSet().setPeerBidiStreamCount(1);
                settings.setPeerBidiStreamCount((short) 128);
            }
            var c = reg.registration.openConfiguration(alpnBuffers, 1, settings, null, null, allocator);
            if (c == null) {
                throw new Exception("failed to create configuration");
            }
            conf = new Configuration(api.apiTable, reg.registration, c, allocator);
            var cred = new QuicCredentialConfig(allocator);
            cred.setType(QUIC_CREDENTIAL_TYPE_NONE);
            int flags = QUIC_CREDENTIAL_FLAG_CLIENT;
            flags |= QUIC_CREDENTIAL_FLAG_NO_CERTIFICATE_VALIDATION;
            cred.setFlags(flags);
            cred.setAllowedCipherSuites(
                QUIC_ALLOWED_CIPHER_SUITE_AES_128_GCM_SHA256 |
                    QUIC_ALLOWED_CIPHER_SUITE_AES_256_GCM_SHA384 |
                    QUIC_ALLOWED_CIPHER_SUITE_CHACHA20_POLY1305_SHA256);
            var err = conf.configuration.loadCredential(cred);
            if (err != 0) {
                throw new RuntimeException("failed to load credential");
            }
        }
        System.out.println("init conn");
        Connection conn;
        {
            var allocator = Allocator.ofUnsafe();
            conn = new SampleConnection(api.apiTable, reg.registration, allocator, ref ->
                reg.registration.openConnection(MsQuicUpcall.connectionCallback, ref.MEMORY, null, allocator));
            if (conn.connection == null) {
                conn.close();
                throw new RuntimeException("failed to create connection");
            }
            int err = conn.connection.start(conf.configuration, QUIC_ADDRESS_FAMILY_INET, new PNIString(allocator, "127.0.0.1"), 443);
            if (err != 0) {
                conn.close();
                throw new RuntimeException("failed to start connection");
            }
        }
        System.out.println("connection started");
    }

    private static class SampleConnection extends Connection {
        public SampleConnection(
            QuicApiTable apiTable, QuicRegistration registration, Allocator allocator, Function<PNIRef<Connection>, QuicConnection> connectionSupplier) {
            super(apiTable, registration, allocator, connectionSupplier);
        }

        @Override
        public int callback(QuicConnectionEvent event) {
            System.out.println("received connection event " + event.getType() + " on thread " + Thread.currentThread());
            super.callback(event);
            switch (event.getType()) {
                case QUIC_CONNECTION_EVENT_CONNECTED -> {
                    var allocator = Allocator.ofUnsafe();
                    var stream = new SampleStream(this, apiTable, registration, connection, allocator, ref ->
                        connection.openStream(0, MsQuicUpcall.streamCallback, ref.MEMORY, null, allocator));
                    if (stream.stream == null) {
                        System.out.println("failed to open stream");
                        stream.close();
                        return 1;
                    }
                    int err = stream.stream.start(0);
                    if (err != 0) {
                        System.out.println("failed to start stream");
                        stream.close();
                        return err;
                    }
                }
                case QUIC_CONNECTION_EVENT_SHUTDOWN_COMPLETE -> {
                    System.out.println("connection shutdown");
                    System.exit(0);
                }
            }
            return 0;
        }
    }

    private static class SampleStream extends Stream {
        private final Connection conn;

        public SampleStream(Connection conn,
                            QuicApiTable apiTable, QuicRegistration registration, QuicConnection connection, Allocator allocator, Function<PNIRef<Stream>, QuicStream> streamSupplier) {
            super(apiTable, registration, connection, allocator, streamSupplier);
            this.conn = conn;
        }

        @Override
        public int callback(QuicStreamEvent event) {
            System.out.println("received stream event " + event.getType() + " on thread " + Thread.currentThread());
            super.callback(event);
            switch (event.getType()) {
                case QUIC_STREAM_EVENT_START_COMPLETE -> {
                    var allocator = Allocator.ofUnsafe();
                    var mem = new PNIString(allocator, "hello world");
                    send(allocator, mem.MEMORY);
                }
                case QUIC_STREAM_EVENT_RECEIVE -> {
                    var data = event.getUnion().getRECEIVE();
                    int count = data.getBufferCount();
                    var bufMem = data.getBuffers().MEMORY;
                    bufMem = bufMem.reinterpret(QuicBuffer.LAYOUT.byteSize() * count);
                    var bufs = new QuicBuffer.Array(bufMem);
                    for (int i = 0; i < count; ++i) {
                        var buf = bufs.get(i);
                        var seg = buf.getBuffer().reinterpret(buf.getLength());
                        System.out.println("Buffer[" + i + "]");
                        Utils.hexDump(seg);
                    }
                    closeStream();
                }
                case QUIC_STREAM_EVENT_SHUTDOWN_COMPLETE ->
                    conn.connection.shutdown(QUIC_CONNECTION_SHUTDOWN_FLAG_NONE, 0);
            }
            return 0;
        }
    }
}
