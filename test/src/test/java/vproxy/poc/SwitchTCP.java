package vproxy.poc;

import vfd.FDs;
import vfd.IP;
import vfd.IPPort;
import vfd.MacAddress;
import vproxy.component.secure.SecurityGroup;
import vproxy.test.tool.CommandServer;
import vproxybase.component.elgroup.EventLoopGroup;
import vproxybase.component.elgroup.EventLoopWrapper;
import vproxybase.connection.ServerSock;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.ByteArray;
import vproxybase.util.Network;
import vserver.impl.Http1ServerImpl;
import vswitch.Switch;
import vswitch.Table;
import vswitch.stack.fd.VSwitchFDContext;
import vswitch.stack.fd.VSwitchFDs;

import java.io.File;
import java.io.FileOutputStream;

public class SwitchTCP {
    public static void main(String[] args) throws Exception {
        EventLoopGroup elg = new EventLoopGroup("elg0");
        Switch sw = new Switch(
            "sw0", new IPPort("127.0.0.1", 18472), elg, 60_000, 60_000, SecurityGroup.allowAll()
        );
        sw.start();
        elg.add("el0");
        EventLoopWrapper el = elg.get("el0");
        SelectorEventLoop loop = el.getSelectorEventLoop();

        String script = "" +
            "sudo ifconfig tap1 172.16.3.55/24\n" +
            "sudo ifconfig tap1 inet6 add fd00::337/120\n";
        File f = File.createTempFile("tap1", ".sh");
        f.deleteOnExit();
        try (var fos = new FileOutputStream(f)) {
            fos.write(script.getBytes());
            fos.flush();
        }
        //noinspection ResultOfMethodCallIgnored
        f.setExecutable(true);

        sw.addTable(3, new Network("172.16.3.0/24"), new Network("[fd00::300]/120"), null);
        sw.addTap("tap1", 3, f.getAbsolutePath(), null);

        Table table = sw.getTable(3);
        table.addIp(IP.from("172.16.3.254"), new MacAddress("00:00:00:00:03:04"), null);

        FDs fds = new VSwitchFDs(new VSwitchFDContext(sw, table, loop.selector));
        ServerSock serverSock = ServerSock.create(new IPPort("0.0.0.0", 80), fds);

        Http1ServerImpl httpServer = new Http1ServerImpl(el);
        httpServer.get("/hello", rctx -> rctx.response().end("world\r\n"));
        ByteArray largeBuffer = ByteArray.allocate(1024 * 1024);
        byte[] chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".getBytes();
        for (int i = 0; i < largeBuffer.length(); ++i) {
            largeBuffer.set(i, chars[i % chars.length]);
        }
        httpServer.get("/large", rctx -> rctx.response().end(largeBuffer));
        httpServer.pst("/validate", rctx -> {
            var body = rctx.body();
            if (body == null) {
                rctx.response().status(400).end("body not provided\r\n");
                return;
            }
            if (body.length() != 1024 * 1024) {
                rctx.response().status(400).end("body length is not 1024 * 1024\r\n");
                return;
            }
            for (int i = 0; i < 1024 * 1024; ++i) {
                if (body.get(i) != chars[i % chars.length]) {
                    rctx.response().status(400).end("invalid char at index " + i +
                        ", expecting " + (char) (chars[i % chars.length]) +
                        ", but got " + ((char) body.get(i)) +
                        "\r\n");
                    return;
                }
            }
            rctx.response().status(200).end("OK\r\n");
        });
        httpServer.listen(serverSock);

        ServerSock serverSock88 = ServerSock.create(new IPPort("0.0.0.0", 88), fds);
        el.addServer(serverSock88, null, new CommandServer());
    }
}
