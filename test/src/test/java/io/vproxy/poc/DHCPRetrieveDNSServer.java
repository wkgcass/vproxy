package io.vproxy.poc;

import io.vproxy.base.dhcp.DHCPClientHelper;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.Callback;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.IP;

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
