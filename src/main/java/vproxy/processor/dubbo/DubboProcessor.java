package vproxy.processor.dubbo;

import vproxy.processor.HeadPayloadProcessor;

public class DubboProcessor extends HeadPayloadProcessor {
    public DubboProcessor() {
        super("dubbo", 16, 12, 4, Integer.MAX_VALUE);
    }
}
