package net.cassite.vproxy.discovery;

public interface NodeListener {
    void join(Node node);

    void down(Node node);

    void leave(Node node);
}
