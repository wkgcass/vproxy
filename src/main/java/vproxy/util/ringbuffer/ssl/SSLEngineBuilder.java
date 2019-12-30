package vproxy.util.ringbuffer.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class SSLEngineBuilder {
    private final Function<SSLContext, SSLEngine> constructor;
    private final List<SSLEngineManipulator> builders = new LinkedList<>();

    public SSLEngineBuilder(Function<SSLContext, SSLEngine> constructor) {
        this.constructor = constructor;
    }

    public void configure(SSLEngineManipulator f) {
        builders.add(f);
    }

    public SSLEngine build(SSLContext ctx, String sni) {
        var engine = constructor.apply(ctx);
        var params = new SSLParameters();
        for (var b : builders) {
            b.manipulate(engine, params, sni);
        }
        engine.setSSLParameters(params);
        return engine;
    }
}
