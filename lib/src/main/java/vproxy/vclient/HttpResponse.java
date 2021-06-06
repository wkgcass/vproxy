package vproxy.vclient;

import vjson.JSON;
import vjson.cs.UTF8ByteArrayCharStream;
import vjson.ex.JsonParseException;
import vproxy.base.util.ByteArray;
import vproxy.vserver.util.ByteArrayCharStream;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public interface HttpResponse {
    int status();

    String header(String key);

    ByteArray body();

    default String bodyAsString() {
        return bodyAsString(StandardCharsets.UTF_8);
    }

    default String bodyAsString(Charset charset) {
        ByteArray body = body();
        if (body == null) {
            return null;
        }
        return new String(body.toJavaArray(), charset);
    }

    default JSON.Instance bodyAsJson() throws JsonParseException {
        return bodyAsJson(StandardCharsets.UTF_8);
    }

    default JSON.Instance bodyAsJson(Charset charset) throws JsonParseException {
        ByteArray body = body();
        if (body == null) {
            return null;
        }
        if (charset == StandardCharsets.UTF_8) {
            return JSON.parse(new UTF8ByteArrayCharStream(body.toJavaArray()));
        } else {
            return JSON.parse(new ByteArrayCharStream(body, charset));
        }
    }

    HttpClientConn conn();
}
