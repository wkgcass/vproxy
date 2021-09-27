package vproxy.base.processor;

import java.util.NoSuchElementException;

public interface ProcessorRegistry {
    Processor get(String name) throws NoSuchElementException;
}
