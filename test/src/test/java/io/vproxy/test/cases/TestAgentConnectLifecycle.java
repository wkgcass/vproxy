package io.vproxy.test.cases;

import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.socks.AddressType;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.vproxyx.websocks.ConfigLoader;
import io.vproxy.vproxyx.websocks.ConfigProcessor;
import io.vproxy.vproxyx.websocks.WebSocksProxyAgentConnectorProvider;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class TestAgentConnectLifecycle {
    static class Fixture implements AutoCloseable {
        final SelectorEventLoop loop=SelectorEventLoop.open();
        final NetEventLoop net=new NetEventLoop(loop);
        final ConnectableConnection conn;
        final ConnectableConnectionHandler handler;
        final ConnectableConnectionHandlerContext ctx;
        int callbacks, failures, successes;
        Runnable reenter;
        boolean throwFromCallback;
        Fixture() throws Exception {
            var provider=new WebSocksProxyAgentConnectorProvider(new ConfigProcessor(new ConfigLoader(),null,null));
            Class<?> type=Class.forName(provider.getClass().getName()+"$AgentConnectableConnectionHandler");
            var ctor=type.getDeclaredConstructor(WebSocksProxyAgentConnectorProvider.class,String.class,AddressType.class,
                String.class,int.class,Consumer.class,boolean.class,boolean.class);
            ctor.setAccessible(true);
            handler=(ConnectableConnectionHandler)ctor.newInstance(provider,"proxy.test",AddressType.domain,"target.test",443,
                (Consumer<Connector>)value -> {
                    ++callbacks; if(value==null)++failures;else ++successes;
                    if(callbacks==1 && reenter!=null)reenter.run();
                    if(throwFromCallback)throw new IllegalStateException("fixture callback failed");
                },true,false);
            var input=new TestConnectionReadFairness.Input(loop,new TestTunReadFairness.ClockFDs(),0);
            conn=ConnectableConnection.wrap(input,input.getRemoteAddress(),new ConnectionOpts(),RingBuffer.allocate(128),RingBuffer.allocate(512));
            net.addConnectableConnection(conn,null,handler);
            ctx=(ConnectableConnectionHandlerContext)loop.getAtt(input);
        }
        void completeHandshake() throws Exception {
            // Reach the actual final parser branch with an independently owned
            // one-byte reply. No socket, TLS, or native TUN permissions needed.
            var step=handler.getClass().getDeclaredField("step");step.setAccessible(true);step.setInt(handler,6);
            var result=handler.getClass().getDeclaredField("socks5ConnectResult");result.setAccessible(true);
            result.set(handler,ByteArrayChannel.fromEmpty(new byte[1]));
            conn.getInBuffer().storeBytesFrom(ByteArrayChannel.fromFull(new byte[]{0}));
            handler.readable(ctx);
        }
        int step() throws Exception {var f=handler.getClass().getDeclaredField("step");f.setAccessible(true);return f.getInt(handler);}
        public void close() throws Exception {conn.close();loop.close();}
    }

    @Test public void timeoutAndLateCloseCompleteFailureOnce() throws Exception {
        try(var f=new Fixture()) {
            f.handler.exception(f.ctx,new SocketTimeoutException("upstream deadline"));
            f.handler.closed(f.ctx);f.handler.exception(f.ctx,new IOException("late transport failure"));
            assertEquals(1,f.callbacks);assertEquals(1,f.failures);assertTrue(f.conn.isClosed());
        }
    }
    @Test public void failureCallbackReentryCannotCompleteTwice() throws Exception {
        try(var f=new Fixture()) {
            f.reenter=()->f.handler.closed(f.ctx);
            f.handler.exception(f.ctx,new IOException("first failure"));
            assertEquals(1,f.callbacks);assertTrue(f.conn.isClosed());
        }
    }
    @Test public void throwingFailureCallbackStillClosesOwnedTransport() throws Exception {
        try(var f=new Fixture()) {
            f.throwFromCallback=true;
            try {f.handler.exception(f.ctx,new IOException("failure"));fail("callback exception required");}
            catch(IllegalStateException expected) {assertEquals("fixture callback failed",expected.getMessage());}
            assertTrue("transport must close even if consumer throws",f.conn.isClosed());
            f.throwFromCallback=false;f.handler.closed(f.ctx);assertEquals(1,f.callbacks);
        }
    }
    @Test public void completedFailureIgnoresLateConnectedAndReadable() throws Exception {
        try(var f=new Fixture()) {
            f.handler.exception(f.ctx,new IOException("failure"));
            f.handler.connected(f.ctx);f.handler.readable(f.ctx);
            assertEquals(7,f.step());assertEquals(1,f.callbacks);assertTrue(f.conn.isClosed());
        }
    }
    @Test public void successfulHandoffIgnoresOldFailureEvents() throws Exception {
        try(var f=new Fixture()) {
            f.completeHandshake();assertEquals(1,f.successes);assertFalse(f.conn.isClosed());
            f.handler.exception(f.ctx,new IOException("stale"));f.handler.closed(f.ctx);f.handler.remoteClosed(f.ctx);
            assertEquals(1,f.callbacks);assertEquals(0,f.failures);assertFalse("handoff owns the transport now",f.conn.isClosed());
        }
    }
    @Test public void successCallbackReentryCannotCloseHandedOffTransport() throws Exception {
        try(var f=new Fixture()) {
            f.reenter=()->f.handler.closed(f.ctx);f.completeHandshake();
            assertEquals(1,f.callbacks);assertEquals(1,f.successes);assertFalse(f.conn.isClosed());
        }
    }
    @Test public void successfulHandoffIgnoresStaleReadable() throws Exception {
        try(var f=new Fixture()) {
            f.completeHandshake();f.handler.readable(f.ctx);
            assertEquals(1,f.callbacks);assertFalse(f.conn.isClosed());
        }
    }
    @Test public void unfinishedRemoteCloseFailsAndClosesTransport() throws Exception {
        try(var f=new Fixture()) {
            f.handler.remoteClosed(f.ctx);
            assertEquals(1,f.failures);assertEquals(0,f.successes);assertTrue(f.conn.isClosed());
        }
    }
}
