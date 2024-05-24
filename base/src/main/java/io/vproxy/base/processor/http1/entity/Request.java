package io.vproxy.base.processor.http1.entity;

import io.vproxy.base.util.ByteArray;

public class Request extends HttpEntity {
    public String method; // notnull
    public String uri; // notnull
    public String version; // nullable

    @SuppressWarnings("DuplicatedCode")
    public ByteArray toByteArray() {
        StringBuilder textPart = new StringBuilder();
        textPart.append(method).append(" ").append(uri);
        if (version != null) {
            textPart.append(" ").append(version);
        }
        textPart.append("\r\n"); // end first line
        return commonToByteArray(textPart);
    }

    @Override
    public String toString() {
        return "Request{" +
            "method='" + method + '\'' +
            ", uri='" + uri + '\'' +
            ", version='" + version + '\'' +
            ", headers=" + headers +
            ", body=" + body +
            ", chunks=" + chunks +
            ", trailers=" + trailers +
            '}';
    }
}
