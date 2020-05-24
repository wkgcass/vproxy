package vproxybase.util.ringbuffer.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class SSLEngineBuilder {
    private final Function<SSLContext, SSLEngine> constructor;
    private final List<Consumer<SSLEngine>> builders = new LinkedList<>();

    public SSLEngineBuilder(Function<SSLContext, SSLEngine> constructor) {
        this.constructor = constructor;
    }

    public void configure(Consumer<SSLEngine> f) {
        builders.add(f);
    }

    public SSLEngine build(SSLContext ctx) {
        var engine = constructor.apply(ctx);
        for (var b : builders) {
            b.accept(engine);
        }
        return engine;
    }
}
