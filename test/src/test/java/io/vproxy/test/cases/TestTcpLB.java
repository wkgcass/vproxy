package vproxy.test.cases;

import org.junit.*;
import vproxy.base.Config;
import vproxy.base.component.check.HealthCheckConfig;
import vproxy.base.component.elgroup.EventLoopGroup;
import vproxy.base.component.svrgroup.Method;
import vproxy.base.component.svrgroup.ServerGroup;
import vproxy.base.connection.Connection;
import vproxy.base.connection.NetEventLoop;
import vproxy.base.connection.Protocol;
import vproxy.base.connection.ServerSock;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Network;
import vproxy.base.util.thread.VProxyThread;
import vproxy.component.app.TcpLB;
import vproxy.component.proxy.Session;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.secure.SecurityGroupRule;
import vproxy.component.svrgroup.Upstream;
import vproxy.test.tool.CaseUtils;
import vproxy.test.tool.Client;
import vproxy.test.tool.EchoServer;
import vproxy.test.tool.IdServer;
import vproxy.vfd.IP;
import vproxy.vfd.IPPort;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

@SuppressWarnings({"Duplicates", "OptionalGetWithoutIsPresent"})
public class TestTcpLB {
    private static final int lbPort = 18080;

    private static SelectorEventLoop serverLoop;
    private static String addressOtherThan127;

    @BeforeClass
    public static void classSetUp() throws Exception {
        serverLoop = SelectorEventLoop.open();
        serverLoop.loop(r -> VProxyThread.create(r, "serverLoop"));
        new EchoServer(serverLoop, 20080); // no need to record the echo server
        NetEventLoop serverNetLoop = new NetEventLoop(serverLoop);
        new IdServer("0", serverNetLoop, 19080);
        new IdServer("1", serverNetLoop, 19081);
        new IdServer("2", serverNetLoop, 19082);

        // find another ip bond to local
        addressOtherThan127 = CaseUtils.ipv4OtherThan127();
        if (addressOtherThan127 == null)
            throw new Exception("this machine do not have a non 127.0.0.1 address");
        new IdServer("3", serverNetLoop, 19082, addressOtherThan127);
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        Thread t = serverLoop.getRunningThread();
        serverLoop.close();
        t.join();
    }

    private Upstream upstream0;
    private EventLoopGroup elg0;
    private SecurityGroup secg0;
    private TcpLB lb0;

    private ServerGroup sg0;
    private ServerGroup sg1;

    private ServerGroup sgEcho;

    private SelectorEventLoop loop;

    private List<Client> clients = new LinkedList<>();

    @Before
    public void setUp() throws Exception {
        upstream0 = new Upstream("ups0");
        elg0 = new EventLoopGroup("elg0");
        elg0.add("el0");
        secg0 = new SecurityGroup("secg0", true);
        lb0 = new TcpLB("lb0", elg0, elg0,
            new IPPort("127.0.0.1", lbPort), upstream0,
            Config.tcpTimeout, 16384, 16384, secg0);
        lb0.start();

        sg0 = new ServerGroup("sg0", elg0, new HealthCheckConfig(400, /* disable health check */24 * 60 * 60 * 1000, 2, 3), Method.wrr);
        sg0.add("svr0", new IPPort("127.0.0.1", 19080), 10);
        sg0.add("svr1", new IPPort("127.0.0.1", 19081), 10);
        // manually set to healthy
        for (ServerGroup.ServerHandle h : sg0.getServerHandles()) {
            h.healthy = true;
        }

        sg1 = new ServerGroup("sg1", elg0, new HealthCheckConfig(400, /* disable health check */24 * 60 * 60 * 1000, 2, 3), Method.wrr);
        sg1.add("svr2", new IPPort("127.0.0.1", 19082), 10);
        // manually set to healthy
        for (ServerGroup.ServerHandle h : sg1.getServerHandles()) {
            h.healthy = true;
        }

        sgEcho = new ServerGroup("sgEcho", elg0, new HealthCheckConfig(400, 1000, 1, 3), Method.wrr);
        sgEcho.add("echo", new IPPort("127.0.0.1", 20080), 10);

        loop = SelectorEventLoop.open();

        loop.loop(r -> VProxyThread.create(r, "Test"));
    }

    @After
    public void tearDown() throws Throwable {
        loop.close();
        elg0.close();
        for (Client c : clients) {
            c.close();
        }
    }

    @Test
    public void simpleProxy() throws Exception {
        // add sgEcho into ups0
        // because we want to test
        // whether backend can receive
        // all data that the frontend sends
        // and frontend can receive
        // all data that the backend replies
        upstream0.add(sgEcho, 10);

        for (int i = 0; i < 3; ++i) {
            Client client = new Client(lbPort);
            client.connect();

            for (int j = 0; j < 3; ++j) {
                String recv = client.sendAndRecv("hello there", 11);
                assertEquals("the response should be the same as request", "hello there", recv);
            }

            client.close();
        }

        // now we know that the lb can proxy data
    }

    @Test
    public void proxyWRR() throws Exception {
        // add sg0 to ups0
        // and we expect one request we get 1,
        // another request we get 2
        upstream0.add(sg0, 10);

        int zero = 0;
        int one = 0;

        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            String recv = client.sendAndRecv("anything", 1);
            assertTrue("response should be 0 or 1", recv.equals("0") || recv.equals("1"));
            if (recv.equals("0")) {
                ++zero;
            } else {
                ++one;
            }
            client.close();
        }
        assertTrue("weight same, so zero count and one count should be the same",
            zero - one > -2 && zero - one < 2); // we allow +-1

        zero = 0;
        one = 0;
        // change weight of svr0 to 5
        sg0.getServerHandles().stream().filter(s -> s.alias.equals("svr0")).findFirst().get().setWeight(5);

        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            String recv = client.sendAndRecv("anything", 1);
            assertTrue("response should be 0 or 1", recv.equals("0") || recv.equals("1"));
            if (recv.equals("0")) {
                ++zero;
            } else {
                ++one;
            }
            client.close();
        }
        assertEquals("weight 1/2, so zero-count / one-count should be 1/2",
            0.5, ((double) zero) / one, 0.1);

        // now we know the wrr works well
    }

    @Test
    public void removeAndAttachBackendOnRunning() throws Exception {
        // add sg0 to ups0
        upstream0.add(sg0, 10);
        sg0.remove("svr1"); // remove 1

        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            String recv = client.sendAndRecv("anything", 1);
            assertEquals("response should be 0 because 1 is removed", "0", recv);
            client.close();
        }

        sg0.add("svr1", new IPPort("127.0.0.1", 19081), 5);
        sg0.getServerHandles().stream().filter(s -> s.alias.equals("svr1")).findFirst().get().healthy = true;

        int zero = 0;
        int one = 0;
        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            String recv = client.sendAndRecv("anything", 1);
            assertTrue("response should be 0 or 1", recv.equals("0") || recv.equals("1"));
            if (recv.equals("0")) {
                ++zero;
            } else {
                ++one;
            }
            client.close();
        }
        assertEquals("1 added back, and weight 2/1, so zero-count / one-count should be 2/1",
            2, ((double) zero) / one, 0.1);
    }

    @Test
    public void attachAndRemoveBackendGroupOnRunning() throws Exception {
        // add sg0 and sg1 to ups0
        // attach
        upstream0.add(sg0, 10);
        upstream0.add(sg1, 10);

        // the response would be
        // "2" : "1" : "0" = 2 : 1 : 1
        int zero = 0;
        int one = 0;
        int two = 0;
        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            String recv = client.sendAndRecv("anything", 1);
            assertTrue("response should be 0 or 1 or 2", recv.equals("0") || recv.equals("1") || recv.equals("2"));
            switch (recv) {
                case "0":
                    ++zero;
                    break;
                case "1":
                    ++one;
                    break;
                default:
                    ++two;
                    break;
            }
            client.close();
        }
        assertEquals("\"2\":\"1\" = 2 : 1",
            2, ((double) two) / one, 0.1);
        assertEquals("\"2\":\"0\" = 2 : 1",
            2, ((double) two) / zero, 0.1);

        // let's set sg0 weight to 5
        Upstream.ServerGroupHandle sh = upstream0.getServerGroupHandles().stream().filter(h -> h.alias.equals("sg0")).findFirst().get();
        sh.setWeight(5);

        // "2" : "1" : "0" = 4 : 1 : 1
        zero = 0;
        one = 0;
        two = 0;
        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            String recv = client.sendAndRecv("anything", 1);
            assertTrue("response should be 0 or 1 or 2", recv.equals("0") || recv.equals("1") || recv.equals("2"));
            switch (recv) {
                case "0":
                    ++zero;
                    break;
                case "1":
                    ++one;
                    break;
                default:
                    ++two;
                    break;
            }
            client.close();
        }
        assertEquals("\"2\":\"1\" = 4 : 1",
            4, ((double) two) / one, 0.2);
        assertEquals("\"2\":\"0\" = 4 : 1",
            4, ((double) two) / zero, 0.2);

        // then let's detach the sg0
        upstream0.remove(sg0);

        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            String recv = client.sendAndRecv("anything", 1);
            assertEquals("response should be 2", "2", recv);
            client.close();
        }
    }

    @Test
    public void backendDead() throws Exception {
        // add sg0 to ups0
        // and we make svr0 dead
        upstream0.add(sg0, 10);
        sg0.getServerHandles().stream().filter(s -> s.alias.equals("svr0")).findFirst().get().healthy = false;

        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            String recv = client.sendAndRecv("anything", 1);
            assertEquals("response should be 1 because 0 is DOWN", "1", recv);
            client.close();
        }
    }

    @Test
    public void proxyWLC() throws Exception {
        // to test wlc, we should select one server and make it DOWN
        // and make a few connections (e.g. 10)
        // then make the server UP
        // and make a few connections (e.g. 5)
        // the new connections should be made to the selected server
        // also we make the selected server weight to 5
        upstream0.add(sg0, 10);
        sg0.setMethod(Method.wlc);
        ServerGroup.ServerHandle h = sg0.getServerHandles().stream().filter(s -> s.alias.equals("svr0")).findFirst().get();
        h.setWeight(5);
        h.healthy = false;

        // make connections
        for (int i = 0; i < 10; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            clients.add(client);
            String recv = client.sendAndRecv("anything", 1);
            assertEquals("response should be 1 because 0 is removed", "1", recv);
            // and we do not release the connections
        }

        // then set it the svr0 to healthy
        h.healthy = true;
        // and make 5 connections
        for (int i = 0; i < 5; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            clients.add(client);
            String recv = client.sendAndRecv("anything", 1);
            assertEquals("response should be 0 because svr1 (weight 10) has 10 connections, " +
                "and svr 0 (weight 5) has 0 connections", "0", recv);
            // and we do not release the connections
        }

        // then, let's make a connection
        Client client = new Client(lbPort);
        client.connect();
        clients.add(client);
        String recv = client.sendAndRecv("anything", 1);
        // we don't know for sure what it is, because random may be added to the list
        // but we know it's 0 or 1
        assertTrue("the response should be either 0 or 1", recv.equals("0") || recv.equals("1"));

        // then we make another request
        client = new Client(lbPort);
        client.connect();
        clients.add(client);
        String newRecv = client.sendAndRecv("anything", 1);
        // this time, it will be another value
        if (recv.equals("0")) {
            assertEquals("the last recv is 0, so this time, recv should be 1", "1", newRecv);
        } else {
            assertEquals("the last recv is 1, so this time, recv should be 0", "0", newRecv);
        }
    }

    @Test
    public void proxySource() throws Exception {
        // all requests should be sent to the same backend

        // usually we do not use "source" proxy with multiple sg
        // so in the test case, we only add one
        upstream0.add(sg0, 10);
        sg0.setMethod(Method.source);

        // make connections
        String resp = null;
        for (int i = 0; i < 100; ++i) {
            Client client = new Client(lbPort);
            client.connect();
            clients.add(client);
            String recv = client.sendAndRecv("anything", 1);
            if (resp == null) {
                resp = recv;
            } else {
                assertEquals("connections should be sent to the same backend because we are using source", "0", recv);
            }
        }
    }

    @Test
    public void changeHealthCheckOnRunning() throws Exception {
        ServerGroup.ServerHandle h = sg0.getServerHandles().stream().findFirst().get();
        h.healthy = false;
        // because the health check period is set to 24 hours
        // so there's no chance that this field will be set back to true
        // and we update the health check config
        // to make it right again
        sg0.setHealthCheckConfig(new HealthCheckConfig(200, 500, 2, 3));
        // we sleep for a few seconds for it to turn up (at least 1 second)
        Thread.sleep(2000);
        assertTrue("the server should turn up", h.healthy);
    }

    @Test
    public void listAndCloseSession() throws Exception {
        upstream0.add(sg0, 10);
        Client client1 = new Client(lbPort);
        client1.connect();
        String id1 = client1.sendAndRecv("anything", 1);

        Client client2 = new Client(lbPort);
        client2.connect();
        String id2 = client2.sendAndRecv("anything", 1);

        // now we have two sessions
        assertEquals("two clients, should have two sessions", 2, lb0.sessionCount());

        // we find the session of svr1
        List<Session> sessions = new LinkedList<>();
        lb0.copySessions(sessions);
        Session svr1Session = sessions.stream().filter(s -> s.passive.remote.getPort() == 19081).findFirst().get();

        Client theAliveClient = id1.equals("0") ? client1 : client2;
        Client theDeadClient = id2.equals("1") ? client2 : client1;

        // we close it
        svr1Session.close();
        // then one client is alive
        assertEquals("one of the clients can still send and receive", "0", theAliveClient.sendAndRecv("anything", 1));
        // and the other is dead
        try {
            theDeadClient.sendAndRecv("a", 1);
            fail("the dead client should fail");
        } catch (IOException ignore) {
        }

        assertEquals("two clients, one down, should have 1 session", 1, lb0.sessionCount());

        theAliveClient.close();
    }

    @Test
    public void listAndCloseConnection() throws Exception {
        upstream0.add(sg0, 10);
        Client client1 = new Client(lbPort);
        client1.connect();
        String id1 = client1.sendAndRecv("anything", 1);

        Client client2 = new Client(lbPort);
        client2.connect();
        String id2 = client2.sendAndRecv("anything", 1);

        // now we have two sessions
        assertEquals("2 clients, should have 4 connections inside the event loop", 4, elg0.get("el0").connectionCount());
        ServerGroup.ServerHandle svr0 = sg0.getServerHandles().stream().filter(s -> s.alias.equals("svr0")).findFirst().get();
        assertEquals("1 connection inside the svr0", 1, svr0.connectionCount());
        ServerGroup.ServerHandle svr1 = sg0.getServerHandles().stream().filter(s -> s.alias.equals("svr1")).findFirst().get();
        assertEquals("1 connection inside the svr1", 1, svr1.connectionCount());

        List<Connection> connections = new LinkedList<>();
        svr1.copyConnections(connections);

        Connection conn = connections.get(0);

        Client theAliveClient = id1.equals("0") ? client1 : client2;
        Client theDeadClient = id2.equals("1") ? client2 : client1;

        // let's close the connection to svr1
        conn.close();

        // then one client is alive
        assertEquals("one of the clients can still send and receive", "0", theAliveClient.sendAndRecv("anything", 1));
        // and the other is dead
        try {
            theDeadClient.sendAndRecv("a", 1);
            fail("the dead client should fail");
        } catch (IOException ignore) {
        }

        assertEquals("2 clients, 1 down, should have 2 connections inside the event loop", 2, elg0.get("el0").connectionCount());
        assertEquals("1 connection inside the svr0", 1, svr0.connectionCount());
        assertEquals("0 connection inside the svr1", 0, svr1.connectionCount());

        theAliveClient.close();
    }

    @Test
    public void listServerSocks() throws Exception {
        assertEquals("should have 1 serverSock inside the event loop", 1, elg0.get("el0").serverCount());
        List<ServerSock> servers = new LinkedList<>();
        elg0.get("el0").copyServers(servers);
        assertEquals("the serverSock should bind " + lbPort, lbPort, servers.get(0).bind.getPort());
    }

    @Test
    public void checkBinBoutAndAcceptedConnections() throws Exception {
        upstream0.add(sg0, 10);
        // let's get some resources that will not change
        ServerGroup.ServerHandle svr0 = sg0.getServerHandles().stream().filter(s -> s.alias.equals("svr0")).findFirst().get();
        ServerGroup.ServerHandle svr1 = sg0.getServerHandles().stream().filter(s -> s.alias.equals("svr1")).findFirst().get();
        ServerSock server = lb0.servers.keySet().stream().findFirst().get();

        assertEquals("the server not connected", 0, server.getHistoryAcceptedConnectionCount());
        assertEquals("the server not connected", 0, server.getFromRemoteBytes());
        assertEquals("the server not connected", 0, server.getToRemoteBytes());
        assertEquals("backend not visited", 0, svr0.getFromRemoteBytes());
        assertEquals("backend not visited", 0, svr0.getToRemoteBytes());
        assertEquals("backend not visited", 0, svr1.getFromRemoteBytes());
        assertEquals("backend not visited", 0, svr1.getToRemoteBytes());

        // we start two clients to send and read data
        Client client1 = new Client(lbPort);
        client1.connect();
        clients.add(client1);
        String id1 = client1.sendAndRecv("client1msg000"/*13*/, 1);
        // send again to make a difference about outBytes between svr0 and svr1
        client1.sendAndRecv("client1msg001"/*13*/, 1);
        Thread.sleep(10);

        assertEquals("the server is connected once", 1, server.getHistoryAcceptedConnectionCount());
        assertEquals("sent 26 bytes and received 2 bytes", 26, server.getFromRemoteBytes());
        assertEquals("sent 26 bytes and received 2 bytes", 2, server.getToRemoteBytes());
        if (id1.equals("0")) {
            assertEquals("sent 26 bytes and received 2 bytes", 2, svr0.getFromRemoteBytes());
            assertEquals("sent 26 bytes and received 2 bytes", 26, svr0.getToRemoteBytes());
        } else {
            assertEquals("sent 26 bytes and received 2 bytes", 2, svr1.getFromRemoteBytes());
            assertEquals("sent 26 bytes and received 2 bytes", 26, svr1.getToRemoteBytes());
        }

        Client client2 = new Client(lbPort);
        client2.connect();
        clients.add(client2);
        String id2 = client2.sendAndRecv("client2msg0"/*11*/, 1);
        Thread.sleep(10);

        assertEquals("the server is connected twice", 2, server.getHistoryAcceptedConnectionCount());
        assertEquals("sent 11 bytes and received 1 byte", 37, server.getFromRemoteBytes());
        assertEquals("sent 11 bytes and received 1 byte", 3, server.getToRemoteBytes());
        if (id2.equals("0")) {
            assertEquals("sent 11 bytes and received 1 byte", 1, svr0.getFromRemoteBytes());
            assertEquals("sent 11 bytes and received 1 byte", 11, svr0.getToRemoteBytes());
        } else {
            assertEquals("sent 11 bytes and received 1 byte", 1, svr1.getFromRemoteBytes());
            assertEquals("sent 11 bytes and received 1 byte", 11, svr1.getToRemoteBytes());
        }

        assertNotEquals("should be dispatched to different servers", id1, id2);

        List<Session> sessions = new LinkedList<>();
        lb0.copySessions(sessions);

        // check sessions
        for (Session s : sessions) {
            int clientWrote;
            int clientRead;
            if (s.passive.remote.getPort() == 19080) {
                if (id1.equals("0")) {
                    clientWrote = 26;
                    clientRead = 2;
                } else {
                    clientWrote = 11;
                    clientRead = 1;
                }
            } else {
                if (id2.equals("0")) {
                    clientWrote = 26;
                    clientRead = 2;
                } else {
                    clientWrote = 11;
                    clientRead = 1;
                }
            }
            assertEquals("sent " + clientWrote + " bytes and received " + clientRead + " bytes",
                clientWrote, s.active.getFromRemoteBytes());
            assertEquals("sent " + clientWrote + " bytes and received " + clientRead + " bytes",
                clientRead, s.active.getToRemoteBytes());
            assertEquals("sent " + clientWrote + " bytes and received " + clientRead + " bytes",
                clientRead, s.passive.getFromRemoteBytes());
            assertEquals("sent " + clientWrote + " bytes and received " + clientRead + " bytes",
                clientWrote, s.passive.getToRemoteBytes());
        }
    }

    @Test
    public void removeAndAddEventLoopOnRunning() throws Exception {
        upstream0.add(sg0, 10);

        Client client1 = new Client(lbPort);
        client1.connect();
        client1.sendAndRecv("anything", 1);

        Client client2 = new Client(lbPort);
        client2.connect();
        client2.sendAndRecv("anything", 1);

        // now we have 2 connections

        // remove a connection
        elg0.remove("el0");

        // now two connections should both be closed
        try {
            client1.sendAndRecv("anything", 1);
            fail("no event loop, connections should be closed");
        } catch (Exception ignore) {
        }
        try {
            client2.sendAndRecv("anything", 1);
            fail("no event loop, connections should be closed");
        } catch (Exception ignore) {
        }

        // add loop back
        elg0.add("el0");
        client1.connect();
        client1.sendAndRecv("anything", 1);
        client2.connect();
        client2.sendAndRecv("anything", 1);
        // this show work properly
    }

    @Test
    public void forbidOnRunning() throws Exception {
        upstream0.add(sg0, 10);

        // start another lb
        TcpLB lb1 = new TcpLB("lb1", elg0, elg0,
            new IPPort("127.0.0.1", lbPort + 1), upstream0,
            Config.tcpTimeout, 16384, 16384, secg0);
        lb1.start();

        // deny lbPort
        SecurityGroupRule secgr0 = new SecurityGroupRule(
            "secgr0", new Network(IP.blockParseAddress("127.0.0.1"), Network.parseMask(32)), Protocol.TCP, lbPort, lbPort, false
        );
        secg0.addRule(secgr0);

        Client client1 = new Client(lbPort);
        client1.connect(); // this is ok because it's not reached the lb yet
        try {
            client1.sendAndRecv("data", 1);
            fail("should be denied by rule");
        } catch (IOException ignore) {
        }

        Client client2 = new Client(lbPort + 1);
        client2.connect();
        client2.sendAndRecv("any", 1);
        // should work properly

        // remove the rule
        secg0.removeRule("secgr0");
        // then client1 should be able to write
        client1.connect();
        client1.sendAndRecv("ok", 1);
    }

    @Test
    public void replaceIp() throws Exception {
        upstream0.add(sg1, 10); // use sg1 because it contain only one backend
        sg1.setHealthCheckConfig(new HealthCheckConfig(100, 500, 2, 3));

        Client client1 = new Client(lbPort);
        client1.connect();
        String res = client1.sendAndRecv("anything", 1);
        assertEquals("requesting svr2 should return 2", "2", res);
        client1.close();

        sg1.replaceIp("svr2", IP.from("1.1.1.1")); // a backend not exist
        // now there should be 2 backend named svr2
        List<ServerGroup.ServerHandle> list = sg1.getServerHandles();
        assertEquals("should be two servers", 2, list.size());
        assertEquals("name should be svr2", "svr2", list.get(0).alias);
        assertTrue("old server is logic deleted", list.get(0).isLogicDelete());
        ServerGroup.ServerHandle old = list.get(0);
        assertEquals("new server name should also be svr2", "svr2", list.get(1).alias);

        Thread.sleep(650); // the health check period is 500ms and connect timeout is 100 ms

        // now there should only be one backend
        list = sg1.getServerHandles();
        assertEquals("should be 1 server", 1, list.size());
        assertSame("the old server should be preserved", old, list.get(0));
        assertFalse("old server is not logic deleted now", old.isLogicDelete());

        // let's try a server that will succeed
        sg1.replaceIp("svr2", IP.from(addressOtherThan127)); // use another one this time
        list = sg1.getServerHandles();
        assertEquals("should be two servers", 2, list.size());

        Thread.sleep(1050); // period 500 and up 2 times

        // now there should only be one backend
        list = sg1.getServerHandles();
        assertEquals("should be 1 server", 1, list.size());
        assertNotSame("the old server is removed", old, list.get(0));
        assertFalse("the new server is not logic deleted", list.get(0).isLogicDelete());
    }
}
