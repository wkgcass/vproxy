package vproxy.test.tool;

import vproxy.component.exception.NoException;
import vproxy.discovery.Discovery;
import vproxy.util.BlockCallback;

import java.util.ArrayList;
import java.util.List;

public class DiscoveryHolder {
    private final List<Discovery> discoveries = new ArrayList<>();

    public void add(Discovery d) {
        discoveries.add(d);
    }

    public void release() {
        for (Discovery d : discoveries) {
            if (d.isClosed())
                continue;
            BlockCallback<Void, NoException> cb = new BlockCallback<>();
            d.close(cb);
            cb.block();
        }
    }
}
