package io.vproxy.poc;

import io.vproxy.base.component.check.CheckProtocol;
import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.util.AnnotationKeys;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.Network;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.dns.DNSServer;
import io.vproxy.vfd.FDs;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vswitch.Switch;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

public class SwitchUDP {
    public static void main(String[] args) throws Exception {
        var elg = new EventLoopGroup("elg0");
        var sw = new Switch(
            "sw0", new IPPort("127.0.0.1", 18472), elg, 60000, 60000, SecurityGroup.allowAll()
        );
        sw.start();
        elg.add("el0");
        var script = ("" +
            "sudo ifconfig tap1 172.16.3.55/24\n" +
            "sudo ifconfig tap1 inet6 add fd00::337/120\n").trim();
        var f = File.createTempFile("tap1", ".sh");
        f.deleteOnExit();
        try (var fos = new FileOutputStream(f)) {
            fos.write(script.getBytes());
            fos.flush();
        }
        //noinspection ResultOfMethodCallIgnored
        f.setExecutable(true);
        sw.addNetwork(3, Network.from("172.16.3.0/24"), Network.from("[fd00::300]/120"), null);
        sw.addTap("tap1", 3, f.getAbsolutePath());
        var network = sw.getNetwork(3);
        network.addIp(IP.from("172.16.3.254"), new MacAddress("00:00:00:00:03:04"), null);
        var fds = network.fds();

        var sg = new ServerGroup("sg0", elg, new HealthCheckConfig(2000, 5000, 1, 1, CheckProtocol.none), Method.wrr);
        sg.setAnnotations(new Annotations(Map.of(
            AnnotationKeys.ServerGroup_HintHost.name, "abc.def.com"
        )));
        sg.add("svr0", new IPPort("12.23.34.45:67"), 10);
        sg.add("svr1", new IPPort("98.76.54.32:10"), 10);
        var ups = new Upstream("ups0");
        ups.add(sg, 10);
        var dns = new DNSServer("dns0", new IPPort("172.16.3.254", 53), elg, ups, 60_000, SecurityGroup.allowAll()) {
            @Override
            protected FDs getFDs() {
                return fds;
            }
        };
        dns.start();
    }
}
