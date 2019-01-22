package net.cassite.vproxy.app;

import net.cassite.vproxy.component.elgroup.EventLoopWrapper;
import net.cassite.vproxy.selector.SelectorEventLoop;

import java.io.IOException;

public class Application {
    private static Application application;

    public static Application get() {
        return application;
    }

    public final EventLoopGroupHolder eventLoopGroupHolder;
    public final ServerGroupHolder serverGroupHolder;
    public final ServerGroupsHolder serverGroupsHolder;
    public final TcpLBHolder tcpLBHolder;
    public final SelectorEventLoop _controlEventLoop;
    public final EventLoopWrapper controlEventLoop;

    private Application() throws IOException {
        this.eventLoopGroupHolder = new EventLoopGroupHolder();
        this.serverGroupHolder = new ServerGroupHolder();
        this.serverGroupsHolder = new ServerGroupsHolder();
        this.tcpLBHolder = new TcpLBHolder();
        this._controlEventLoop = SelectorEventLoop.open();
        this.controlEventLoop = new EventLoopWrapper("ControlEventLoop", this._controlEventLoop);
    }

    static void create() throws IOException {
        application = new Application();
    }
}
