package vserver;

import vjson.JSON;
import vproxybase.util.ByteArray;
import vproxybase.util.HttpStatusCodeReasonMap;

public interface HttpResponse {
    default HttpResponse status(int code) {
        return status(code, HttpStatusCodeReasonMap.get(code));
    }

    HttpResponse status(int code, String msg);

    HttpResponse header(String key, String value);

    HttpResponse sendHeadersWithChunked();

    default void end() {
        end((ByteArray) null);
    }

    default void end(JSON.Instance inst) {
        header("Content-Type", "application/json")
            .end(inst.stringify());
    }

    default void end(String str) {
        end(ByteArray.from(str.getBytes()));
    }

    void end(ByteArray body);

    HttpResponse sendChunk(ByteArray chunk);
}
