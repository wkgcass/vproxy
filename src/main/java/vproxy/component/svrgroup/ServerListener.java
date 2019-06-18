package vproxy.component.svrgroup;

public interface ServerListener {
    void up(ServerGroup.ServerHandle server);

    void down(ServerGroup.ServerHandle server);

    void start(ServerGroup.ServerHandle server);

    void stop(ServerGroup.ServerHandle server);
}
