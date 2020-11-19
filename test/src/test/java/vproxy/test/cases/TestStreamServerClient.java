package vproxy.test.cases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import vlibbase.Conn;
import vclient.StreamClient;
import vproxy.test.tool.Client;
import vproxybase.util.BlockCallback;
import vproxybase.util.ByteArray;
import vserver.StreamServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class TestStreamServerClient {
    private StreamServer server;
    private StreamClient client;
    private static final int listenPort = 30080;

    @Before
    public void setUp() {
        server = StreamServer.create();
        client = StreamClient.to("127.0.0.1", listenPort);
    }

    @After
    public void tearDown() {
        server.close();
        client.close();
    }

    @Test
    public void simpleServer() throws IOException {
        BlockCallback<String, IOException> cb = new BlockCallback<>();

        server.accept(conn -> {
            var sb = new StringBuilder();
            conn.data(data -> {
                String s = new String(data.toJavaArray());
                if (s.equals("quit\r\n")) {
                    cb.succeeded(sb.toString());
                    conn.close();
                } else {
                    sb.append(s);
                    conn.write(data);
                }
            }).closed(() ->
                cb.failed(new IOException("closed before data fully read"))
            );
        }).listen(listenPort);

        var client = new Client(listenPort);
        client.connect();
        for (String s : Arrays.asList("hello\r\n", "world\r\n", "foo\r\n", "bar\r\n")) {
            assertEquals(s, client.sendAndRecv(s, s.length()));
        }
        client.sendAndRecv("quit\r\n", 0);
        assertEquals("hello\r\nworld\r\nfoo\r\nbar\r\n", cb.block());
    }

    @Test
    public void simpleClient() throws Exception {
        server.accept(conn -> {
            conn.data(conn::write);
            conn.closed(() -> {
            });
        }).listen(listenPort);

        BlockingQueue<String> dataQ = new LinkedBlockingDeque<>();
        BlockCallback<Conn, IOException> connCb = new BlockCallback<>();
        client.connect((err, conn) -> {
            if (err != null) {
                connCb.failed(err);
                return;
            }

            conn.data(data -> dataQ.add(new String(data.toJavaArray())));
            conn.closed(() -> {
            });
            connCb.succeeded(conn);
        });
        Conn conn = connCb.block();
        for (String s : Arrays.asList("hello", "world", "foo", "bar")) {
            conn.write(ByteArray.from(s.getBytes()));
            String r = dataQ.poll(1_000, TimeUnit.MILLISECONDS);
            assertEquals(s, r);
        }
        conn.close();
    }
}
