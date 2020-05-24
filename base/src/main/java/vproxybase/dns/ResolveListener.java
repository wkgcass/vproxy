package vproxybase.dns;

public interface ResolveListener {
    void onResolve(Cache cache);

    void onRemove(Cache cache);
}
