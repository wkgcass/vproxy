package net.cassite.vproxy.dns;

import net.cassite.vproxy.util.Callback;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

public interface IResolver {
    void resolve(String host, Callback<? super InetAddress, ? super UnknownHostException> cb);

    void resolve(String host, boolean ipv4, boolean ipv6, Callback<? super InetAddress, ? super UnknownHostException> cb);

    void resolveV6(String host, Callback<? super Inet6Address, ? super UnknownHostException> cb);

    void resolveV4(String host, Callback<? super Inet4Address, ? super UnknownHostException> cb);

    int cacheCount();

    void copyCache(Collection<? super Resolver.Cache> cacheList);
}
