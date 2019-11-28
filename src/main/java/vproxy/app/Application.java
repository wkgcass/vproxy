package vproxy.app;

import vfd.VFDConfig;
import vproxy.app.mesh.SmartGroupDelegateHolder;
import vproxy.app.mesh.SmartNodeDelegateHolder;
import vproxy.component.elgroup.EventLoopWrapper;
import vproxy.component.exception.AlreadyExistException;
import vproxy.component.exception.ClosedException;
import vproxy.component.exception.NotFoundException;
import vproxy.connection.ServerSock;
import vproxy.selector.SelectorEventLoop;
import vproxy.util.Logger;

import java.io.IOException;

public class Application {
    public static final String DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME = "(acceptor-elg)";
    public static final String DEFAULT_ACCEPTOR_EVENT_LOOP_NAME = "(acceptor-el)";
    public static final String DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME = "(worker-elg)";
    public static final String DEFAULT_WORKER_EVENT_LOOP_NAME_PREFIX = "(worker-el";
    public static final String DEFAULT_WORKER_EVENT_LOOP_NAME_SUFFIX = ")";
    public static final String DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME = "(control-elg)";
    public static final String DEFAULT_CONTROL_EVENT_LOOP_NAME = "(control-el)";
    public static final String VERSION = "1.0.0-BETA-6"; // _THE_VERSION_

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
    public final CertKeyHolder certKeyHolder;

    public final EventLoopWrapper controlEventLoop;
    public final RESPControllerHolder respControllerHolder;
    public final HttpControllerHolder httpControllerHolder;

    public final SmartGroupDelegateHolder smartGroupDelegateHolder;
    public final SmartNodeDelegateHolder smartNodeDelegateHolder;

    private Application() throws IOException {
        this.version = VERSION;

        this.eventLoopGroupHolder = new EventLoopGroupHolder();
        this.serverGroupHolder = new ServerGroupHolder();
        this.serverGroupsHolder = new ServerGroupsHolder();
        this.tcpLBHolder = new TcpLBHolder();
        this.securityGroupHolder = new SecurityGroupHolder();
        this.certKeyHolder = new CertKeyHolder();
        SelectorEventLoop _controlEventLoop = SelectorEventLoop.open();
        this.controlEventLoop = new EventLoopWrapper("ControlEventLoop", _controlEventLoop);
        this.respControllerHolder = new RESPControllerHolder();
        this.socks5ServerHolder = new Socks5ServerHolder();
        this.httpControllerHolder = new HttpControllerHolder();

        this.smartGroupDelegateHolder = new SmartGroupDelegateHolder();
        this.smartNodeDelegateHolder = new SmartNodeDelegateHolder();
    }

    public static boolean isDefaultEventLoopGroupName(String name) {
        return name.equals(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME)
            || name.equals(DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME)
            || name.equals(DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME);
    }

    static void create() throws IOException {
        application = new Application();

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
        if (VFDConfig.useFStack) {
            cores = 1; // f-stack applications have only one thread
        }
        for (int i = 0; i < cores; ++i) {
            try {
                application.eventLoopGroupHolder.get(DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME).add(
                    DEFAULT_WORKER_EVENT_LOOP_NAME_PREFIX + i + DEFAULT_WORKER_EVENT_LOOP_NAME_SUFFIX
                );
            } catch (AlreadyExistException | ClosedException | NotFoundException e) {
                throw new IOException("create default worker event loop failed", e);
            }
        }
        if (ServerSock.supportReusePort()) {
            assert Logger.lowLevelDebug("use worker event loop as the acceptor event loop");
            application.eventLoopGroupHolder.map.put(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME, application.eventLoopGroupHolder.map.get(DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME));
        } else {
            assert Logger.lowLevelDebug("create one thread for default acceptor");
            try {
                application.eventLoopGroupHolder.add(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME);
                application.eventLoopGroupHolder.get(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME).add(DEFAULT_ACCEPTOR_EVENT_LOOP_NAME);
            } catch (AlreadyExistException | NotFoundException | ClosedException e) {
                throw new IOException("create default acceptor event loop failed", e);
            }
        }
    }
}
