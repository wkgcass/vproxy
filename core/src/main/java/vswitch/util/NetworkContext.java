package vswitch.util;

import java.util.UUID;

public class NetworkContext {
    public final String id;

    public NetworkContext() {
        this(UUID.randomUUID().toString());
    }

    public NetworkContext(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return id + " ::: ";
    }
}
