package vproxy.processor.common;

import vproxy.processor.HeadPayloadProcessor;

public class CommonInt32FramedProcessor extends HeadPayloadProcessor {
    public CommonInt32FramedProcessor() {
        super("framed-int32", 4, 0, 4, Integer.MAX_VALUE);
    }
}
