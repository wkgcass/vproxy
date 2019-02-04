package net.cassite.vproxy.component.khala;

import net.cassite.vproxy.discovery.Node;

public interface KhalaNodeListener {
    void add(Node n, KhalaNode node);

    void remove(Node n, KhalaNode node);
}
