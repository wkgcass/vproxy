package vproxy.discovery;

public interface NodeListener {
    void up(Node node);

    void down(Node node);

    void leave(Node node);
}
