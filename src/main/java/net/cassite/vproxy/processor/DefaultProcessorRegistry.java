package net.cassite.vproxy.processor;

import net.cassite.vproxy.processor.dubbo.DubboProcessor;
import net.cassite.vproxy.processor.http2.Http2Processor;
import net.cassite.vproxy.processor.common.CommonInt32FramedProcessor;

import java.util.HashMap;
import java.util.Map;

public class DefaultProcessorRegistry implements ProcessorRegistry {
    public static final DefaultProcessorRegistry instance = new DefaultProcessorRegistry();

    private Map<String, Processor> registry = new HashMap<>();

    private DefaultProcessorRegistry() {
        register(new Http2Processor());
        register(new CommonInt32FramedProcessor());
        register(new DubboProcessor());
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
