package io.vproxy.vproxyx.websocks;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.selector.wrap.quic.QuicFDs;
import io.vproxy.base.util.Logger;
import io.vproxy.msquic.MsQuicUtils;
import io.vproxy.msquic.QuicCredentialConfig;
import io.vproxy.msquic.wrap.Configuration;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PooledAllocator;
import io.vproxy.pni.array.IntArray;
import io.vproxy.vfd.FDs;

import java.io.IOException;
import java.util.List;

public class WebSocksQuicHelper {
    private WebSocksQuicHelper() {
    }

    private static FDs initQuicGeneral(EventLoopGroup loop,
                                       ConfigLoader clientConfig,
                                       String certpem, String keypem) throws IOException {
        var reg = loop.getMsquicRegistration();
        var tmpAlloc = Allocator.ofAuto();
        var alpn = List.of("h3");

        var retInt = new IntArray(tmpAlloc, 1);
        assert Logger.lowLevelDebug("before init msquic configuration");
        Configuration conf;
        try (var tmpAllocator = Allocator.ofConfined()) {
            var settings = MsQuicUtils.newSettings(90_000, tmpAllocator);
            var alpnBuffers = MsQuicUtils.newAlpnBuffers(alpn, tmpAllocator);

            var confAllocator = PooledAllocator.ofUnsafePooled();
            var conf_ = reg.opts.registrationQ
                .openConfiguration(alpnBuffers, alpn.size(), settings, null, retInt, confAllocator);
            if (conf_ == null) {
                confAllocator.close();
                throw new IOException(STR."ConfigurationOpen failed: \{retInt.get(0)}");
            }
            conf = new Configuration(new Configuration.Options(reg, conf_, confAllocator));
            QuicCredentialConfig cred;
            if (clientConfig != null) {
                cred = MsQuicUtils.newClientCredential(!clientConfig.isVerifyCert(), tmpAllocator);
                if (clientConfig.getQuicCacertsPath() != null) {
                    cred.setCaCertificateFile(clientConfig.getQuicCacertsPath(), tmpAllocator);
                }
            } else {
                cred = MsQuicUtils.newServerCredential(certpem, keypem, tmpAllocator);
            }
            int err = conf.opts.configurationQ.loadCredential(cred);
            if (err != 0) {
                conf.close();
                throw new IOException("ConfigurationLoadCredential failed");
            }
        }

        return new QuicFDs(true, reg, conf, alpn);
    }

    public static FDs initClientQuic(EventLoopGroup loop, ConfigLoader loader) throws IOException {
        return initQuicGeneral(loop, loader, null, null);
    }

    public static FDs initServerQuic(EventLoopGroup loop, String certPem, String keyPem) throws IOException {
        return initQuicGeneral(loop, null, certPem, keyPem);
    }
}
