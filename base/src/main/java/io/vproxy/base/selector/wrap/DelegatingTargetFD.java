package io.vproxy.base.selector.wrap;

public interface DelegatingTargetFD extends VirtualFD {
    void setReadable();

    void setWritable();

    void cancelReadable();

    void cancelWritable();
}
