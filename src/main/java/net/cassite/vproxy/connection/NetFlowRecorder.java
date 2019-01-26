package net.cassite.vproxy.connection;

public interface NetFlowRecorder {
    void incToRemoteBytes(long bytes);

    void incFromRemoteBytes(long bytes);

    long getToRemoteBytes();

    long getFromRemoteBytes();
}
