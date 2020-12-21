package vproxy.poc;

import vfd.IP;
import vproxybase.dhcp.DHCPClientHelper;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.util.Callback;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.VProxyThread;

import java.io.IOException;
import java.util.Set;

public class DHCPRetrieveDNSServer {
    public static void main(String[] args) throws Exception {
        SelectorEventLoop loop = SelectorEventLoop.open();
        loop.loop(r -> new VProxyThread(r, "dhcp"));

        DHCPClientHelper.getDomainNameServers(loop, n -> true, 1, new Callback<>() {
            @Override
            protected void onSucceeded(Set<IP> value) {
                Logger.alert("retrieved dns servers: " + value);
            }

            @Override
            protected void onFailed(IOException err) {
                Logger.error(LogType.ALERT, "failed to get dns servers", err);
                Logger.printStackTrace(err);
            }

            @Override
            protected void doFinally() {
                try {
                    loop.close();
                } catch (IOException ignore) {
                }
            }
        });
    }
}
