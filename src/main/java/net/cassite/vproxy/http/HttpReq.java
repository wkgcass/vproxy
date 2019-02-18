package net.cassite.vproxy.http;

import java.util.LinkedList;
import java.util.List;

public class HttpReq {
    public StringBuilder method;
    public StringBuilder url;
    public StringBuilder version = new StringBuilder();
    HttpHeader currentParsingHeader;
    public final List<HttpHeader> headers = new LinkedList<>();
    int bodyLen = 0;
    public StringBuilder body;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(" ").append(url).append(" ").append(version).append("\\r\\n\n");
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
