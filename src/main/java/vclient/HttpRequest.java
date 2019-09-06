package vclient;

import vjson.JSON;
import vproxy.util.ByteArray;

public interface HttpRequest {
    HttpRequest header(String key, String value);

    default void send(ResponseHandler handler) {
        send((ByteArray) null, handler);
    }

    default void send(JSON.Instance inst, ResponseHandler handler) {
        header("Content-Type", "application/json").
            send(inst.stringify(), handler);
    }

    default void send(String s, ResponseHandler handler) {
        send(ByteArray.from(s.getBytes()), handler);
    }

    void send(ByteArray body, ResponseHandler handler);
}
