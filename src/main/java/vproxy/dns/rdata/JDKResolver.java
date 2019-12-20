package vproxy.dns.rdata;

import vproxy.dns.AbstractResolver;
import vproxy.util.Callback;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class JDKResolver extends AbstractResolver {
    public JDKResolver(String alias) throws IOException {
        super(alias);
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
