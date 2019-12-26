package vproxy.dns;

import vfd.DatagramFD;
import vfd.FDProvider;
import vproxy.util.Callback;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VResolver extends AbstractResolver {
    private static final InetAddress[] LOCALHOST;

    static {
        LOCALHOST = new InetAddress[]{
            Utils.l3addr("127.0.0.1"),
            Utils.l3addr("::1")
        };
    }

    private static final int reloadConfigFilePeriod = 30_000;
    private static final int DNS_REQ_TIMEOUT = 1_500;
    private static final int MAX_RETRY = 2;

    private final DatagramFD sock;
    private Map<String, InetAddress> hosts;
    private final DNSClient client;

    public VResolver(String alias, List<InetSocketAddress> initialNameServers, Map<String, InetAddress> initialHosts) throws IOException {
        super(alias, FDProvider.get().getProvided());
        this.hosts = initialHosts;

        DatagramFD sock = null;
        DNSClient client = null;
        try {
            sock = FDProvider.get().openDatagramFD();
            sock.configureBlocking(false);
            sock.bind(new InetSocketAddress(Utils.l3addr(new byte[]{0, 0, 0, 0}), 0)); // bind any port
            client = new DNSClient(loop.getSelectorEventLoop(), sock, initialNameServers, DNS_REQ_TIMEOUT, MAX_RETRY);
        } catch (IOException e) {
            try {
                loop.getSelectorEventLoop().close();
            } catch (IOException ignore) {
            }
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException ignore) {
                }
            }
            //noinspection ConstantConditions
            if (client != null) {
                try {
                    client.close();
                } catch (Throwable ignore) {
                }
            }
            throw e;
        }
        this.sock = sock;
        this.client = client;

        loop.getSelectorEventLoop().period(reloadConfigFilePeriod, () -> {
            var nameServers = Resolver.getNameServers();
            this.client.setNameServers(nameServers);
            var hosts = Resolver.getHosts();
            if (!hosts.isEmpty()) {
                this.hosts = hosts;
            }
        }); // no need to record the periodic event, when the resolver is shutdown, the loop would be shutdown as well
    }

    private InetAddress[] listToArray(List<InetAddress> list) {
        InetAddress[] ret = new InetAddress[list.size()];
        return list.toArray(ret);
    }

    private InetAddress[] searchInHosts(String domain) {
        if (domain.equals("localhost")) {
            return LOCALHOST;
        }
        if (hosts.containsKey(domain)) {
            return new InetAddress[]{hosts.get(domain)};
        }
        return null;
    }

    @Override
    protected void getAllByName(String domain, Callback<InetAddress[], UnknownHostException> cb) {
        {
            InetAddress[] result = searchInHosts(domain);
            if (result != null) {
                cb.succeeded(result);
                return;
            }
        }

        List<InetAddress> addresses = new ArrayList<>();
        final int MAX_STEP = 2;
        int[] step = {0};
        class TmpCB extends Callback<List<InetAddress>, UnknownHostException> {
            @Override
            protected void onSucceeded(List<InetAddress> value) {
                addresses.addAll(value);
                ++step[0];
                if (step[0] == MAX_STEP) {
                    // should end the process
                    cb.succeeded(listToArray(addresses));
                }
            }

            @Override
            protected void onFailed(UnknownHostException err) {
                ++step[0];
                if (step[0] == MAX_STEP) {
                    // should end the process
                    if (addresses.isEmpty()) { // no process found address, so raise the exception
                        cb.failed(err);
                    } else {
                        cb.succeeded(listToArray(addresses));
                    }
                }
            }
        }
        client.resolveIPv4(domain, new TmpCB());
        client.resolveIPv6(domain, new TmpCB());
    }

    @Override
    public void stop() throws IOException {
        super.stop();
        sock.close();
    }
}
