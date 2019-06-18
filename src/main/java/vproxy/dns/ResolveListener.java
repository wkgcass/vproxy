package vproxy.dns;

public interface ResolveListener {
    void onResolve(Resolver.Cache cache);

    void onRemove(Resolver.Cache cache);
}
