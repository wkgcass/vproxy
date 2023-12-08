package io.vproxy.base.util.misc;

public interface WithUserData {
    Object getUserData(Object key);

    Object putUserData(Object key, Object value);

    Object removeUserData(Object key);
}
