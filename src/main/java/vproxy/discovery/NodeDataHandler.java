package vproxy.discovery;

import vjson.JSON;
import vproxy.util.Callback;

public interface NodeDataHandler {
    boolean canHandle(String type);

    void handle(int version, String type, JSON.Instance data, Callback<JSON.Instance, Throwable> cb);
}
