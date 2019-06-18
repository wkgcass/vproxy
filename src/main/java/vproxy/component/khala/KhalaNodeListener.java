package vproxy.component.khala;

import vproxy.discovery.Node;

public interface KhalaNodeListener {
    void add(Node n, KhalaNode node);

    void remove(Node n, KhalaNode node);
}
