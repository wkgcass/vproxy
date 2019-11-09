package vproxy.selector.wrap;

import vfd.FD;

public interface VirtualFD extends FD {
    void onRegister();

    void onRemove();
}
