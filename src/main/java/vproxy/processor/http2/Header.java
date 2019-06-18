package vproxy.processor.http2;

public class Header {
    final String keyStr;
    final byte[] key;
    final byte[] value;

    public Header(String key, String value) {
        this.keyStr = key.toLowerCase();
        this.key = keyStr.getBytes();
        this.value = value.getBytes();
    }
}
