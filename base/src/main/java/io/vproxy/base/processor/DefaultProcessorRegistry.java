package io.vproxy.base.processor;

import io.vproxy.base.processor.common.CommonInt32FramedProcessor;
import io.vproxy.base.processor.dubbo.DubboProcessor;
import io.vproxy.base.processor.http.GeneralHttpProcessor;
import io.vproxy.base.processor.http1.HttpProcessor;
import io.vproxy.base.processor.httpbin.BinaryHttpProcessor;
import io.vproxy.base.processor.httpbin.HttpVersion;

import java.util.HashMap;
import java.util.Map;

public class DefaultProcessorRegistry implements ProcessorRegistry {
    public static final DefaultProcessorRegistry instance = new DefaultProcessorRegistry();

    private final Map<String, Processor> registry = new HashMap<>();

    private DefaultProcessorRegistry() {
        register(new BinaryHttpProcessor(HttpVersion.HTTP2));
        register(new CommonInt32FramedProcessor());
        register(new DubboProcessor());
        register(new HttpProcessor());
        register(new GeneralHttpProcessor());
    }

    public static DefaultProcessorRegistry getInstance() {
        return instance;
    }

    /**
     * Register a processor<br>
     * This is a work around when the ServiceLoader cannot find impl of ProcessorRegistry.
     *
     * @param processor the processor to register
     * @throws IllegalArgumentException the name of the processor already registered
     */
    public void register(Processor processor) throws IllegalArgumentException {
        String name = processor.name();
        if (registry.containsKey(name)) {
            throw new IllegalArgumentException("processor for protocol " + name + " already exists");
        }
        registry.put(name, processor);
    }

    @Override
    public Processor get(String name) {
        return registry.get(name);
    }
}
