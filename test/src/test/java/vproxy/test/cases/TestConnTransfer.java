package vproxy.test.cases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vclient.StreamClient;
import vlibbase.Conn;
import vlibbase.ConnRefPool;
import vproxybase.util.BlockCallback;
import vproxybase.util.ByteArray;
import vserver.StreamServer;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestConnTransfer {
    private static final int tcpPort = 30080;

    private ConnRefPool pool;
    private StreamClient client;
    private StreamServer server;

    @Before
    public void setUp() {
        pool = ConnRefPool.create(10);
        client = StreamClient.to("127.0.0.1", tcpPort);
        server = StreamServer.create();
    }

    @After
    public void tearDown() {
        pool.close();
        client.close();
        server.close();
    }

    @Test
    public void tcpcli2pool() throws Exception {
        server.accept(conn -> {
            conn.data(conn::write);
            conn.closed(() -> {
            });
        }).listen(tcpPort);

        BlockCallback<Conn, IOException> connCb = new BlockCallback<>();
        client.connect((err, conn) -> {
            if (err != null) {
                connCb.failed(err);
                return;
            }
            connCb.succeeded(conn);
        });
        var conn = connCb.block();

        assertEquals(0, pool.count());
        assertTrue(conn.isValidRef());
        assertFalse(conn.isTransferring());
        conn.transferTo(pool);
        assertFalse(conn.isValidRef());
        assertFalse(conn.isTransferring());
        Thread.sleep(10);
        assertEquals(1, pool.count());

        var refOpt = pool.get();
        assertTrue(refOpt.isPresent());
        assertEquals(0, pool.count());
        var ref = refOpt.get();
        assertTrue(ref.isValidRef());
        assertTrue(ref.isTransferring());
        conn = ref.transferTo(client);
        assertFalse(ref.isValidRef());
        assertFalse(ref.isTransferring());

        BlockCallback<String, IOException> cb = new BlockCallback<>();
        conn.data(data -> cb.succeeded(new String(data.toJavaArray())));
        conn.closed(() -> {
        });
        conn.write(ByteArray.from("abc"));
        assertEquals("abc", cb.block());
        conn.close();
    }
}
