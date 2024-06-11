package io.vproxy.base.processor.http1.entity;

import io.vproxy.base.util.ByteArray;

public class Response extends HttpEntity {
    public String version; // notnull
    public int statusCode; // notnull
    public String reason; // notnull for http/1.x

    @Override
    public ByteArray toByteArray() {
        StringBuilder textPart = new StringBuilder();
        textPart.append(version).append(" ").append(statusCode).append(" ").append(reason).append("\r\n");
        return commonToByteArray(textPart);
    }

    @Override
    public String toString() {
        return "Response{" +
            "version='" + version + '\'' +
            ", statusCode=" + statusCode +
            ", reason='" + reason + '\'' +
            ", headers=" + headers +
            ", body=" + body +
            ", isPlain=" + isPlain +
            ", chunks=" + chunks +
            ", trailers=" + trailers +
            '}';
    }
}
