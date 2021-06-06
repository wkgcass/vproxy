package vproxy.vserver;

public interface RoutingHandler {
    void accept(RoutingContext rctx);
}
