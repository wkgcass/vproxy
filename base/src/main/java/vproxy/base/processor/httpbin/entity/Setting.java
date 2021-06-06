package vproxy.base.processor.httpbin.entity;

public class Setting {
    public static final int SETTINGS_HEADER_TABLE_SIZE = 0x1;
    public static final int SETTINGS_ENABLE_PUSH = 0x2;
    public static final int SETTINGS_MAX_CONCURRENT_STREAMS = 0x3;
    public static final int SETTINGS_INITIAL_WINDOW_SIZE = 0x4;
    public static final int SETTINGS_MAX_FRAME_SIZE = 0x5;
    public static final int SETTINGS_MAX_HEADER_LIST_SIZE = 0x6;

    // https://tools.ietf.org/html/rfc8441
    public static final int SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x8;

    public final int identifier;
    public final int value;

    public Setting(int identifier, int value) {
        this.identifier = identifier;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Setting{" +
            "id=" + identifier +
            ", value=" + value +
            '}';
    }
}
