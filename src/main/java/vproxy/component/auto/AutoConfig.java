package vproxy.component.auto;

import vproxy.component.khala.Khala;
import vproxy.discovery.Discovery;

public class AutoConfig {
    public final Discovery discovery;
    public final Khala khala;

    public AutoConfig(Khala khala) {
        this.discovery = khala.discovery;
        this.khala = khala;
    }
}
