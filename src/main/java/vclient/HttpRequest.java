package vclient;

import vjson.JSON;
import vproxy.util.ByteArray;

import java.io.IOException;

public interface HttpRequest {
    HttpRequest header(String key, String value);

    default void send(ResponseHandler handler) throws IOException {
        send((ByteArray) null, handler);
    }

    default void send(JSON.Instance inst, ResponseHandler handler) throws IOException {
        header("Content-Type", "application/json").
            send(inst.stringify(), handler);
    }

    default void send(String s, ResponseHandler handler) throws IOException {
        send(ByteArray.from(s.getBytes()), handler);
    }

    void send(ByteArray body, ResponseHandler handler) throws IOException;
}
