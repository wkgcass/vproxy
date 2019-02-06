package net.cassite.vproxy.app;

import net.cassite.vproxy.app.mesh.AutoLBHolder;
import net.cassite.vproxy.app.mesh.SidecarHolder;
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
    public final SecurityGroupHolder securityGroupHolder;
    public final EventLoopWrapper controlEventLoop;
    public final RESPControllerHolder respControllerHolder;
    public final Socks5ServerHolder socks5ServerHolder;

    public final SidecarHolder sidecarHolder;
    public final AutoLBHolder autoLBHolder;

    private Application() throws IOException {
        this.eventLoopGroupHolder = new EventLoopGroupHolder();
        this.serverGroupHolder = new ServerGroupHolder();
        this.serverGroupsHolder = new ServerGroupsHolder();
        this.tcpLBHolder = new TcpLBHolder();
        this.securityGroupHolder = new SecurityGroupHolder();
        SelectorEventLoop _controlEventLoop = SelectorEventLoop.open();
        this.controlEventLoop = new EventLoopWrapper("ControlEventLoop", _controlEventLoop);
        this.respControllerHolder = new RESPControllerHolder();
        this.socks5ServerHolder = new Socks5ServerHolder();

        this.sidecarHolder = new SidecarHolder();
        this.autoLBHolder = new AutoLBHolder();
    }

    static void create() throws IOException {
        application = new Application();
    }
}
