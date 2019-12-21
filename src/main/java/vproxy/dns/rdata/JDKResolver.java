package vproxy.dns.rdata;

import vfd.FDProvider;
import vfd.VFDConfig;
import vfd.jdk.ChannelFDs;
import vproxy.dns.AbstractResolver;
import vproxy.util.Callback;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class JDKResolver extends AbstractResolver {
    public JDKResolver(String alias) throws IOException {
        super(alias, VFDConfig.useFStack
            ? ChannelFDs.get()
            : FDProvider.get().getProvided());
    }

    @Override
    protected void getAllByName(String domain, Callback<InetAddress[], UnknownHostException> cb) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(domain);
        } catch (UnknownHostException e) {
            cb.failed(e);
            return;
        }
        cb.succeeded(addresses);
    }
}
