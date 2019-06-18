package vproxy.http;

import java.util.LinkedList;
import java.util.List;

public class HttpResp {
    public StringBuilder version;
    public StringBuilder statusCode;
    public StringBuilder statusMessage;
    HttpHeader currentHeader;
    public final List<HttpHeader> headers = new LinkedList<>();
    int bodyLen;
    public StringBuilder body;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(version).append(" ").append(statusCode).append(" ").append(statusMessage).append("\\r\\n\n");
        for (HttpHeader h : headers) {
            sb.append(h.key.toString().trim()).append(": ").append(h.value.toString().trim()).append("\\r\\n\n");
        }
        sb.append("\\r\\n\n");
        if (body != null) {
            sb.append(body);
        }
        return sb.toString();
    }
}
