package io.vproxy.vproxyx.nexus;

import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.msquic.wrap.Configuration;
import io.vproxy.msquic.wrap.Registration;

public class NexusContext {
    public static final int GENERAL_TIMEOUT = 120_000;
    public static final int CONTROL_STREAM_TIMEOUT = 10_000;
    public static final String ALPN = "proxy-nexus";

    public final String selfNodeName;
    public final Nexus nexus;
    public final ResHolder resources;
    public final NetEventLoop loop;
    public final Registration registration;
    public final Configuration clientConfiguration;
    public final Configuration serverConfiguration;
    public final boolean debug;

    public NexusContext(String selfNodeName, Nexus nexus, ResHolder resources, NetEventLoop loop, Registration registration,
                        Configuration clientConfiguration, Configuration serverConfiguration,
                        boolean debug) {
        this.selfNodeName = selfNodeName;
        this.nexus = nexus;
        this.resources = resources;
        this.loop = loop;
        this.registration = registration;
        this.clientConfiguration = clientConfiguration;
        this.serverConfiguration = serverConfiguration;
        this.debug = debug;
    }
}
