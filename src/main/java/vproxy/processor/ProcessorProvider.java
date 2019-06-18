package vproxy.processor;

import java.util.NoSuchElementException;
import java.util.ServiceLoader;

public class ProcessorProvider {
    private static final ProcessorProvider instance = new ProcessorProvider();

    private final ProcessorRegistry registry;
    private final DefaultProcessorRegistry defaultRegistry = DefaultProcessorRegistry.getInstance();

    private ProcessorProvider() {
        ServiceLoader<ProcessorRegistry> loader = ServiceLoader.load(ProcessorRegistry.class);
        registry = loader.findFirst().orElse(null);
    }

    public static ProcessorProvider getInstance() {
        return instance;
    }

    public Processor get(String name) throws NoSuchElementException {
        Processor p = null;
        if (registry != null) {
            try {
                p = registry.get(name);
            } catch (NoSuchElementException ignore) {
            }
        }
        if (p != null)
            return p;
        p = defaultRegistry.get(name);
        if (p == null)
            throw new NoSuchElementException("cannot find processor with name: " + name);
        return p;
    }
}
