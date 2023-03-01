package io.vproxy.base.util.log;

public enum ProbeType {
    VIRTUAL_FD_EVENT("virtual-fd-event"),
    STREAMED_ARQ_UDP_EVENT("streamed-arq-udp-event"),
    STREAMED_ARQ_UDP_RECORD("streamed-arq-udp-record"),
    ;
    public final String name;

    ProbeType(String name) {
        this.name = name;
    }

    public static ProbeType of(String value) {
        if (VIRTUAL_FD_EVENT.name.equals(value)) return VIRTUAL_FD_EVENT;
        if (STREAMED_ARQ_UDP_EVENT.name.equals(value)) return STREAMED_ARQ_UDP_EVENT;
        if (STREAMED_ARQ_UDP_RECORD.name.equals(value)) return STREAMED_ARQ_UDP_RECORD;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
