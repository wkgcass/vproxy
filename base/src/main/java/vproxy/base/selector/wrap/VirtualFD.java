package vproxy.base.selector.wrap;

import vproxy.vfd.FD;

public interface VirtualFD extends FD {
    void onRegister();

    void onRemove();
}
