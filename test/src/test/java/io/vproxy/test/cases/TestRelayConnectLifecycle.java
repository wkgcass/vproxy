package io.vproxy.test.cases;

import io.vproxy.base.connection.*;
import io.vproxy.base.protocol.ProtocolHandler;
import io.vproxy.base.protocol.ProtocolHandlerContext;
import io.vproxy.base.socks.AddressType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.base.util.log.LogHandler;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.component.proxy.ConnectorGen;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vproxyx.websocks.ConfigLoader;
import io.vproxy.vproxyx.websocks.ConfigProcessor;
import io.vproxy.vproxyx.websocks.WebSocksProxyAgentConnectorProvider;
import io.vproxy.vproxyx.websocks.relay.DomainBinder;
import io.vproxy.vproxyx.websocks.relay.RelayBindAnyPortServer;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class TestRelayConnectLifecycle {
    static class Provider extends WebSocksProxyAgentConnectorProvider {
        final List<Consumer<Connector>> pending = new ArrayList<>();
        Consumer<Consumer<Connector>> synchronous;
        Provider() { super(new ConfigProcessor(new ConfigLoader(), null, null)); }
        @Override public void provide(Connection accepted, AddressType type, String address, int port,
                                      Consumer<Connector> callback) {
            assertEquals(AddressType.domain, type);
            assertEquals("relay.test", address);
            assertEquals(443, port);
            pending.add(callback);
            if (synchronous != null) synchronous.accept(callback);
        }
    }
    static class OwnedConnector extends Connector {
        int closes;
        OwnedConnector() { super(new IPPort("192.0.2.9", 8443)); }
        @Override public void close() { ++closes; }
    }
    static class Fixture implements AutoCloseable {
        final Provider provider = new Provider();
        final Connection connection;
        final ProtocolHandler<Tuple<Void, Callback<Connector, IOException>>> handler;
        final ProtocolHandlerContext<Tuple<Void, Callback<Connector, IOException>>> ctx;
        final List<String> improperCallbacks = new ArrayList<>();
        final LogHandler logs = r -> { if (r.content.equals("callback already called")) improperCallbacks.add(r.content); };
        int succeeded, failed;
        Connector handedOff;
        Runnable reenter;
        @SuppressWarnings("unchecked")
        Fixture(boolean mapped) throws Exception {
            var binder = new DomainBinder(null, Network.from("100.64.0.0/24"));
            IP local = mapped ? binder.assignForDomain("relay.test", 0) : IP.from("100.64.0.9");
            var input = new TestConnectionReadFairness.Input(null, null, 0) {
                @Override public IPPort getLocalAddress() { return new IPPort(local, 443); }
            };
            connection = Connection.wrap(input, new ConnectionOpts(), RingBuffer.allocate(64), RingBuffer.allocate(64));
            var server = new RelayBindAnyPortServer(provider, binder, null);
            // Obtain the same per-connection protocol handler as launchUserSpace,
            // without requiring native TUN privileges or opening external sockets.
            var c = Class.forName(RelayBindAnyPortServer.class.getName() + "$RelayBindAnyPortServerConnectorGen")
                .getDeclaredConstructor(RelayBindAnyPortServer.class);
            c.setAccessible(true);
            handler = ((ConnectorGen<Void>) c.newInstance(server)).handler();
            ctx = new ProtocolHandlerContext<>(connection.id(), connection, null, handler);
            handler.init(ctx);
            ctx.data = new Tuple<>(ctx.data.left, new Callback<>() {
                @Override protected void onSucceeded(Connector value) { ++succeeded; handedOff = value; if (reenter != null) reenter.run(); }
                @Override protected void onFailed(IOException error) { ++failed; if (reenter != null) reenter.run(); }
            });
            Logger.logDispatcher.addLogHandler(logs);
        }
        void moreFrontendData() {
            if (!connection.isClosed())
                assertEquals(1, ctx.inBuffer.storeBytesFrom(ByteArrayChannel.fromFull(new byte[]{42})));
            handler.readable(ctx);
        }
        void assertNoDuplicateCallback() { assertTrue(improperCallbacks.toString(), improperCallbacks.isEmpty()); }
        @Override public void close() { Logger.logDispatcher.removeLogHandler(logs); connection.close(); }
    }

    @Test public void repeatedReadinessCreatesOnePendingUpstreamAndKeepsFrontendBytes() throws Exception {
        try (var f = new Fixture(true)) {
            for (int i = 0; i < 8; ++i) f.moreFrontendData();
            assertEquals("one upstream attempt per frontend", 1, f.provider.pending.size());
            assertEquals(8, f.ctx.inBuffer.used());
            assertEquals(0, f.succeeded + f.failed);
            assertTrue(f.handler.closeOnRemoval(f.ctx));
        }
    }
    @Test public void failedAttemptCannotRestartOnReadableOrFailureCallbackReentry() throws Exception {
        try (var f = new Fixture(true)) {
            f.moreFrontendData();
            f.reenter = f::moreFrontendData;
            f.provider.pending.getFirst().accept(null);
            f.moreFrontendData();
            assertEquals(1, f.provider.pending.size());
            assertEquals(1, f.failed); assertEquals(0, f.succeeded);
            assertTrue(f.handler.closeOnRemoval(f.ctx));
            f.assertNoDuplicateCallback();
        }
    }
    @Test public void successfulHandoffCannotRestartAndDoesNotCloseItsConnector() throws Exception {
        try (var f = new Fixture(true)) {
            f.moreFrontendData(); f.reenter = f::moreFrontendData;
            var connector = new OwnedConnector();
            f.provider.pending.getFirst().accept(connector);
            f.moreFrontendData(); f.handler.end(f.ctx);
            assertEquals(1, f.provider.pending.size()); assertEquals(1, f.succeeded); assertEquals(0, f.failed);
            assertSame(connector, f.handedOff); assertEquals(0, connector.closes);
            assertFalse(f.handler.closeOnRemoval(f.ctx)); f.assertNoDuplicateCallback();
        }
    }
    @Test public void frontendEndClosesLateSuccessfulConnectorWithoutAnotherCompletion() throws Exception {
        try (var f = new Fixture(true)) {
            f.moreFrontendData(); f.handler.end(f.ctx);
            var connector = new OwnedConnector(); f.provider.pending.getFirst().accept(connector);
            f.moreFrontendData();
            assertEquals(1, f.provider.pending.size()); assertEquals(1, f.failed); assertEquals(0, f.succeeded);
            assertEquals(1, connector.closes); assertTrue(f.connection.isClosed());
            assertTrue(f.handler.closeOnRemoval(f.ctx)); f.assertNoDuplicateCallback();
        }
    }
    @Test public void frontendTimeoutThenUpstreamTimeoutDoesNotRepeatFailure() throws Exception {
        try (var f = new Fixture(true)) {
            f.moreFrontendData(); f.handler.exception(f.ctx, new SocketTimeoutException("frontend fixture"));
            f.provider.pending.getFirst().accept(null);
            assertEquals(1, f.failed); assertEquals(0, f.succeeded);
            assertTrue(f.connection.isClosed()); f.assertNoDuplicateCallback();
        }
    }
    @Test public void frontendEndThenUpstreamFailureDoesNotRepeatFailure() throws Exception {
        try (var f = new Fixture(true)) {
            f.moreFrontendData(); f.handler.end(f.ctx); f.provider.pending.getFirst().accept(null);
            assertEquals(1, f.failed); f.assertNoDuplicateCallback();
        }
    }
    @Test public void duplicateProviderFailureOrLateSuccessIsDiscardedAndReleased() throws Exception {
        try (var f = new Fixture(true)) {
            f.moreFrontendData(); var cb = f.provider.pending.getFirst(); cb.accept(null); cb.accept(null);
            var connector = new OwnedConnector(); cb.accept(connector);
            assertEquals(1, f.failed); assertEquals(0, f.succeeded); assertEquals(1, connector.closes);
            f.assertNoDuplicateCallback();
        }
    }
    @Test public void missingDomainCannotRepeatFailureOrStartUpstream() throws Exception {
        try (var f = new Fixture(false)) {
            f.moreFrontendData(); f.moreFrontendData(); f.handler.end(f.ctx);
            assertEquals(0, f.provider.pending.size()); assertEquals(1, f.failed); f.assertNoDuplicateCallback();
        }
    }
    @Test public void synchronousProviderCompletionMarksStateBeforeReentry() throws Exception {
        for (boolean success : new boolean[]{false, true}) {
            try (var f = new Fixture(true)) {
                var connector = new OwnedConnector(); f.reenter = () -> {
                    // Avoid unbounded old-code recursion; still detect the second provide.
                    if (f.provider.pending.size() < 2) f.moreFrontendData();
                };
                f.provider.synchronous = cb -> cb.accept(success ? connector : null);
                f.moreFrontendData();
                assertEquals(1, f.provider.pending.size()); assertEquals(1, f.failed + f.succeeded);
                assertEquals(success, !f.handler.closeOnRemoval(f.ctx)); f.assertNoDuplicateCallback();
            }
        }
    }
}
