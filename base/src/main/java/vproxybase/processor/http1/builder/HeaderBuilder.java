package vproxybase.processor.http1.builder;

import vproxybase.processor.http1.entity.Header;

public class HeaderBuilder {
    public StringBuilder key = new StringBuilder();
    public StringBuilder value = new StringBuilder();

    public Header build() {
        Header h = new Header();
        h.key = key.toString();
        h.value = value.toString();
        return h;
    }

    @Override
    public String toString() {
        return "HeaderBuilder{" +
            "key=" + key +
            ", value=" + value +
            '}';
    }
}
