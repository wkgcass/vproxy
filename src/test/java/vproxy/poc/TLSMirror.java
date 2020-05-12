package vproxy.poc;

import vclient.HttpClient;
import vfd.IPPort;
import vjson.JSON;
import vjson.util.ObjectBuilder;
import vmirror.Mirror;
import vproxy.dns.Resolver;
import vproxy.util.OS;
import vproxy.util.ringbuffer.SSLUtils;

import java.io.File;
import java.io.FileOutputStream;

public class TLSMirror {
    public static void main(String[] args) throws Exception {
        if (OS.isWindows()) {
            System.setProperty("vfd", "windows");
        } else {
            System.setProperty("vfd", "posix");
        }

        JSON.Instance config = new ObjectBuilder()
            .put("enabled", true)
            .putArray("mirrors", mirrors -> mirrors
                .addObject(m -> m
                    .put("tap", "tap3")
                    .put("mtu", 47)
                    .putArray("origins", origins -> origins
                        .addObject(o -> o
                            .put("origin", "ssl")
                            .putArray("filters", filters -> filters
                                .addObject(f -> {
                                })
                            )
                        )
                    )
                )
            )
            .build();
        File tmpF = File.createTempFile("mirror", ".json");
        FileOutputStream fos = new FileOutputStream(tmpF);
        fos.write(config.stringify().getBytes());
        fos.flush();
        fos.close();

        Mirror.init(tmpF.getAbsolutePath());

        System.out.println("wait for 10 seconds before start");
        Thread.sleep(5000);
        System.out.println("wait for 5 seconds before start");
        Thread.sleep(1000);
        System.out.println("wait for 4 seconds before start");
        Thread.sleep(1000);
        System.out.println("wait for 3 seconds before start");
        Thread.sleep(1000);
        System.out.println("wait for 2 seconds before start");
        Thread.sleep(1000);
        System.out.println("wait for 1 seconds before start");
        Thread.sleep(1000);
        System.out.println("start");

        var l3addr = Resolver.getDefault().blockResolve("cip.cc");
        var cli = HttpClient.to(new IPPort(l3addr, 443), new HttpClient.Options()
            .setHost("cip.cc")
            .setSSLContext(SSLUtils.getDefaultClientSSLContext()));
        cli.get("/").header("User-Agent", "curl/vproxy").send((err, resp) -> {
            if (err != null) {
                err.printStackTrace();
            } else {
                System.out.println(resp);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
            cli.close();
            Resolver.stopDefault();
            Mirror.destroy();
        });
    }
}
