package net.cassite.vproxy.app;

import net.cassite.vproxy.component.elgroup.EventLoopWrapper;
import net.cassite.vproxy.selector.SelectorEventLoop;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;

public class Application {
    private static Application application;

    public static Application get() {
        return application;
    }

    public final EventLoopGroupHolder eventLoopGroupHolder;
    public final ServerGroupHolder serverGroupHolder;
    public final ServerGroupsHolder serverGroupsHolder;
    public final TcpLBHolder tcpLBHolder;
    public final EventLoopWrapper controlEventLoop;
    public final RESPControllerHolder respControllerHolder;
    public final String appVersion;

    private Application() throws IOException {
        this.eventLoopGroupHolder = new EventLoopGroupHolder();
        this.serverGroupHolder = new ServerGroupHolder();
        this.serverGroupsHolder = new ServerGroupsHolder();
        this.tcpLBHolder = new TcpLBHolder();
        SelectorEventLoop _controlEventLoop = SelectorEventLoop.open();
        this.controlEventLoop = new EventLoopWrapper("ControlEventLoop", _controlEventLoop);
        this.respControllerHolder = new RESPControllerHolder();
        this.appVersion = getVersion();
    }

    static void create() throws IOException {
        application = new Application();
    }

    private String getVersion() {
        try {
            final String MANIFEST_PATH = "/META-INF/MANIFEST.MF";
            InputStream input = this.getClass().getResourceAsStream(MANIFEST_PATH);
            Manifest mainfest = new Manifest(input);
            String version = mainfest.getMainAttributes().getValue("version");
            return version;
        } catch (Exception e) {
            System.out.println(e);
            return "Not Find Version";
        }
    }
}
