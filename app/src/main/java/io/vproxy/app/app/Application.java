package io.vproxy.app.app;

import io.vproxy.base.Config;
import io.vproxy.base.component.elgroup.EventLoopWrapper;
import io.vproxy.base.component.elgroup.dummy.DelegateEventLoopGroup;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Version;
import io.vproxy.base.util.exception.AlreadyExistException;
import io.vproxy.base.util.exception.ClosedException;
import io.vproxy.base.util.exception.NotFoundException;
import io.vproxy.vfd.VFDConfig;

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
    public final UpstreamHolder upstreamHolder;
    public final TcpLBHolder tcpLBHolder;
    public final Socks5ServerHolder socks5ServerHolder;
    public final DNSServerHolder dnsServerHolder;
    public final SecurityGroupHolder securityGroupHolder;
    public final CertKeyHolder certKeyHolder;
    public final SwitchHolder switchHolder;
    public final BPFObjectHolder bpfObjectHolder;

    public final EventLoopWrapper controlEventLoop;
    public final RESPControllerHolder respControllerHolder;
    public final HttpControllerHolder httpControllerHolder;
    public final DockerNetworkPluginControllerHolder dockerNetworkPluginControllerHolder;
    public final PluginHolder pluginHolder;

    private Application() throws IOException {
        this.version = Version.VERSION;

        this.eventLoopGroupHolder = new EventLoopGroupHolder();
        this.serverGroupHolder = new ServerGroupHolder();
        this.upstreamHolder = new UpstreamHolder();
        this.tcpLBHolder = new TcpLBHolder();
        this.securityGroupHolder = new SecurityGroupHolder();
        this.certKeyHolder = new CertKeyHolder();
        SelectorEventLoop _controlEventLoop = SelectorEventLoop.open();
        this.controlEventLoop = new EventLoopWrapper("ControlEventLoop", _controlEventLoop);
        this.respControllerHolder = new RESPControllerHolder();
        this.socks5ServerHolder = new Socks5ServerHolder();
        this.httpControllerHolder = new HttpControllerHolder();
        this.dockerNetworkPluginControllerHolder = new DockerNetworkPluginControllerHolder();
        this.dnsServerHolder = new DNSServerHolder();
        this.switchHolder = new SwitchHolder();
        this.bpfObjectHolder = new BPFObjectHolder();
        this.pluginHolder = new PluginHolder();
    }

    public static boolean isDefaultEventLoopGroupName(String name) {
        return name.equals(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME)
            || name.equals(DEFAULT_CONTROL_EVENT_LOOP_GROUP_NAME)
            || name.equals(DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME);
    }

    public static void createMinimum() throws IOException {
        create(true);
    }

    static void create() throws IOException {
        create(false);
    }

    private static void create(boolean minimum) throws IOException {
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
        if (minimum) {
            cores = 1;
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
        if (VFDConfig.useFStack || (ServerSock.supportReusePort() && Config.supportReusePortLB()) || minimum) {
            assert Logger.lowLevelDebug("use worker event loop as the acceptor event loop");
            application.eventLoopGroupHolder.map.put(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME,
                new DelegateEventLoopGroup(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME,
                    application.eventLoopGroupHolder.map.get(DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME)));
        } else {
            assert Logger.lowLevelDebug("create one thread for default acceptor");
            try {
                application.eventLoopGroupHolder.add(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME);
                application.eventLoopGroupHolder.get(DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME).add(DEFAULT_ACCEPTOR_EVENT_LOOP_NAME);
            } catch (AlreadyExistException | NotFoundException | ClosedException e) {
                throw new IOException("create default acceptor event loop failed", e);
            }
        }
        // start ControlEventLoop
        Application.get().controlEventLoop.loop();
    }
}
