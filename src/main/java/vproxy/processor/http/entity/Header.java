package vproxy.processor.http.entity;

public class Header {
    public String key; // notnull
    public String value; // notnull

    public Header() {
    }

    public Header(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Header{" +
            "key='" + key + '\'' +
            ", value='" + value + '\'' +
            '}';
    }
}
