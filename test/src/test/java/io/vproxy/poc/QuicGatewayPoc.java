package io.vproxy.poc;

import io.vproxy.base.component.check.HealthCheckConfig;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.svrgroup.Method;
import io.vproxy.base.component.svrgroup.ServerGroup;
import io.vproxy.base.util.AnnotationKeys;
import io.vproxy.base.util.Annotations;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.component.ssl.CertKey;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.test.cases.TestSSL;
import io.vproxy.vfd.IPPort;

import java.util.List;
import java.util.Map;

public class QuicGatewayPoc {
    public static void main(String[] args) throws Exception {
        var eventLoopGroup = new EventLoopGroup("quic", new Annotations(Map.of(
            AnnotationKeys.EventLoopGroup_UseMsQuic.name, "true"
        )));

        var ups = new Upstream("ups0");
        var backend = new ServerGroup("sg0", eventLoopGroup, HealthCheckConfig.ofTcpDefault(), Method.wrr);
        ups.add(backend, 10);

        backend.add("svr0", new IPPort("127.0.0.1:33445"), 10);

        var ck = new CertKey("ck0", new String[]{TestSSL.TEST_CERT}, TestSSL.TEST_KEY);

        var quicGw = new QuicGateway("quic0", eventLoopGroup,
            new IPPort("0.0.0.0:443"), ups,
            600_000, 16384, 16384,
            List.of("proto-x", "proto-y"),
            ck, SecurityGroup.allowAll());
        quicGw.start();
    }
}
