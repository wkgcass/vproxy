package vproxy.processor.http.entity;

public class Header {
    public String key; // notnull
    public String value; // notnull

    @Override
    public String toString() {
        return "Header{" +
            "key='" + key + '\'' +
            ", value='" + value + '\'' +
            '}';
    }
}
