package vproxy.base.processor.common;

import vproxy.base.processor.HeadPayloadProcessor;

public class CommonInt32FramedProcessor extends HeadPayloadProcessor {
    public CommonInt32FramedProcessor() {
        super("framed-int32", 4, 0, 4, Integer.MAX_VALUE);
    }
}
