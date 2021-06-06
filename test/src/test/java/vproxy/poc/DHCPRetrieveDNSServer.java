package vproxy.poc;

import vproxy.base.dhcp.DHCPClientHelper;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.util.Callback;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.thread.VProxyThread;
import vproxy.vfd.IP;

import java.io.IOException;
import java.util.Set;

public class DHCPRetrieveDNSServer {
    public static void main(String[] args) throws Exception {
        SelectorEventLoop loop = SelectorEventLoop.open();
        loop.loop(r -> VProxyThread.create(r, "dhcp"));

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
