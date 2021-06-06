package vproxy.base.dns;

public interface ResolveListener {
    void onResolve(Cache cache);

    void onRemove(Cache cache);
}
