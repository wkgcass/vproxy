package vproxy.app;

import vproxy.app.mesh.SidecarHolder;
import vproxy.app.mesh.SmartLBGroupHolder;
import vproxy.component.elgroup.EventLoopWrapper;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.selector.SelectorEventLoop;

import java.io.IOException;

public class Application {
    public static final String DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME = "(acceptor-elg)";
    public static final String DEFAULT_ACCEPTOR_EVENT_LOOP_NAME = "(acceptor-el)";
    public static final String DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME = "(worker-elg)";
    public static final String DEFAULT_WORKER_EVENT_LOOP_NAME_PREFIX = "(worker-el";
    public static final String DEFAULT_WORKER_EVENT_LOOP_NAME_SUFFIX = ")";
    public static final String DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME = "(control-elg)";
    public static final String DEFAULT_CONTROL_EVENT_LOOP_NAME = "(control-el)";

    private static Application application;

    public static Application get() {
        return application;
    }

    public final String version;

    public final EventLoopGroupHolder eventLoopGroupHolder;
    public final ServerGroupHolder serverGroupHolder;
    public final ServerGroupsHolder serverGroupsHolder;
    public final TcpLBHolder tcpLBHolder;
    public final Socks5ServerHolder socks5ServerHolder;
    public final SecurityGroupHolder securityGroupHolder;

    public final EventLoopWrapper controlEventLoop;
    public final RESPControllerHolder respControllerHolder;

    public final SidecarHolder sidecarHolder;
    public final SmartLBGroupHolder smartLBGroupHolder;

    private Application() throws IOException {
        this.version = "1.0.0-BETA-2"; // _THE_VERSION_

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
        this.smartLBGroupHolder = new SmartLBGroupHolder();
    }

    public static boolean isDefaultEventLoopGroupName(String name) {
        return name.equals(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME)
            || name.equals(DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME)
            || name.equals(DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME);
    }

    static void create() throws IOException {
        application = new Application();

        // create one thread for default acceptor
        try {
            application.eventLoopGroupHolder.add(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME);
            application.eventLoopGroupHolder.get(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME).add(DEFAULT_ACCEPTOR_EVENT_LOOP_NAME);
        } catch (AlreadyExistException | NotFoundException | ClosedException e) {
            throw new IOException("create default acceptor event loop failed", e);
        }
        // create one thread for controlling
        try {
            application.eventLoopGroupHolder.add(DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME);
            application.eventLoopGroupHolder.get(DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME).add(DEFAULT_CONTROL_EVENT_LOOP_NAME);
        } catch (AlreadyExistException | NotFoundException | ClosedException e) {
            throw new IOException("create default control event loop failed", e);
        }
        // create event loop group for workers
        try {
            application.eventLoopGroupHolder.add(DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME);
        } catch (AlreadyExistException e) {
            throw new IOException("create default worker event loop group failed", e);
        }
        // use the current core count
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cores; ++i) {
            try {
                application.eventLoopGroupHolder.get(DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME).add(
                    DEFAULT_WORKER_EVENT_LOOP_NAME_PREFIX + i + DEFAULT_WORKER_EVENT_LOOP_NAME_SUFFIX
                );
            } catch (AlreadyExistException | ClosedException | NotFoundException e) {
                throw new IOException("create default worker event loop failed", e);
            }
        }
    }

    void clear() {
        Application.get().eventLoopGroupHolder.clear();
        Application.get().serverGroupHolder.clear();
        Application.get().serverGroupsHolder.clear();
        Application.get().tcpLBHolder.clear();
        Application.get().socks5ServerHolder.clear();
    }
}
