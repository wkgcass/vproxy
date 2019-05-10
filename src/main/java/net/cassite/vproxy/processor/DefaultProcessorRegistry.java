package net.cassite.vproxy.processor;

import net.cassite.vproxy.processor.http2.Http2Processor;

import java.util.HashMap;
import java.util.Map;

public class DefaultProcessorRegistry implements ProcessorRegistry {
    private Map<String, Processor> registry = new HashMap<>() {{
        {
            Http2Processor h2 = new Http2Processor();
            put(h2.name(), h2);
        }
    }};

    DefaultProcessorRegistry() {
    }

    @Override
    public Processor get(String name) {
        return registry.get(name);
    }
}
