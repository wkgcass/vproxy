package vproxybase.dns;

import vfd.*;
import vproxybase.util.Callback;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VResolver extends AbstractResolver {
    private static final IP[] LOCALHOST;

    static {
        LOCALHOST = new IP[]{
            IP.from("127.0.0.1"),
            IP.from("::1")
        };
    }

    private static final int reloadConfigFilePeriod = 30_000;
    private static final int DNS_REQ_TIMEOUT = 1_500;
    private static final int MAX_RETRY = 2;

    private final DatagramFD sock;
    private Map<String, IP> hosts;
    private final DNSClient client;

    public VResolver(String alias, FDs fds) throws IOException {
        super(alias, fds);
        this.hosts = Resolver.getHosts();

        DatagramFD sock = null;
        DNSClient client = null;
        try {
            sock = fds.openDatagramFD();
            sock.configureBlocking(false);
            sock.bind(new IPPort(IP.from(new byte[]{0, 0, 0, 0}), 0)); // bind any port

            // use empty nameserver list to construct the client, it will be filled later
            client = new DNSClient(loop.getSelectorEventLoop(), sock, DNS_REQ_TIMEOUT, MAX_RETRY);
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

        loop.getSelectorEventLoop().period(reloadConfigFilePeriod, () -> Resolver.getNameServers(nameServers -> {
            this.client.setNameServers(nameServers);
            var hosts = Resolver.getHosts();
            if (!hosts.isEmpty()) {
                this.hosts = hosts;
            }
        })); // no need to record the periodic event, when the resolver is shutdown, the loop would be shutdown as well
    }

    public DNSClient getClient() {
        return client;
    }

    private IP[] listToArray(List<IP> list) {
        IP[] ret = new IP[list.size()];
        return list.toArray(ret);
    }

    private IP[] searchInHosts(String domain) {
        if (hosts.containsKey(domain)) {
            IP addr = hosts.get(domain);
            if (domain.equals("localhost") || domain.equals("localhost.")) {
                IP[] ret = new IP[2];
                if (addr instanceof IPv4) {
                    ret[0] = addr;
                    ret[1] = LOCALHOST[1];
                } else {
                    ret[0] = LOCALHOST[0];
                    ret[1] = addr;
                }
                return ret;
            } else {
                return new IP[]{};
            }
        }
        if (domain.equals("localhost") || domain.equals("localhost.")) {
            return LOCALHOST;
        }
        return null;
    }

    @Override
    protected void getAllByName(String domain, Callback<IP[], UnknownHostException> cb) {
        {
            IP[] result = searchInHosts(domain);
            if (result != null) {
                cb.succeeded(result);
                return;
            }
        }

        List<IP> addresses = new ArrayList<>();
        final int MAX_STEP = 2;
        int[] step = {0};
        class TmpCB extends Callback<List<IP>, UnknownHostException> {
            @Override
            protected void onSucceeded(List<IP> value) {
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
