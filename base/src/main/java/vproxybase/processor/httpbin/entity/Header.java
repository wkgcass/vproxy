package vproxybase.processor.httpbin.entity;

public class Header {
    public final String keyStr;
    public final byte[] key;
    public final byte[] value;
    public final boolean caseSensitive;

    public Header(String key, String value) {
        this(key.toLowerCase().getBytes(), value.getBytes(), false);
    }

    public Header(byte[] key, byte[] value, boolean caseSensitive) {
        this.key = key;
        this.value = value;
        if (caseSensitive) {
            this.keyStr = new String(key);
        } else {
            this.keyStr = new String(key).toLowerCase();
        }
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String toString() {
        return "Header{" +
            "key='" + keyStr + '\'' +
            ", value='" + new String(value) + '\'' +
            ", caseSensitive=" + caseSensitive +
            '}';
    }
}
