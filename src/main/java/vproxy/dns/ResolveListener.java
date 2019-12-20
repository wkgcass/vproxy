package vproxy.dns;

public interface ResolveListener {
    void onResolve(Cache cache);

    void onRemove(Cache cache);
}
