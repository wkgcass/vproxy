package io.vproxy.base.selector.wrap;

import io.vproxy.vfd.FD;

public interface VirtualFD extends FD {
    void onRegister();

    void onRemove();
}
