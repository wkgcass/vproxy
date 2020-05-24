package vproxybase.processor.common;

import vproxybase.processor.HeadPayloadProcessor;

public class CommonInt32FramedProcessor extends HeadPayloadProcessor {
    public CommonInt32FramedProcessor() {
        super("framed-int32", 4, 0, 4, Integer.MAX_VALUE);
    }
}
