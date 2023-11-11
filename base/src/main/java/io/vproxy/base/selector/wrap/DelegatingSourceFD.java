package io.vproxy.base.selector.wrap;

public interface DelegatingSourceFD extends VirtualFD {
    void setDelegatingTargetFD(DelegatingTargetFD fd);
}
