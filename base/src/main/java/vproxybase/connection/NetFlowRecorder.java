package vproxybase.connection;

public interface NetFlowRecorder {
    void incToRemoteBytes(long bytes);

    void incFromRemoteBytes(long bytes);
}
