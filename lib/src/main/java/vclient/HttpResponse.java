package vclient;

import vjson.JSON;
import vjson.ex.JsonParseException;
import vproxybase.util.ByteArray;
import vserver.util.ByteArrayCharStream;

public interface HttpResponse {
    int status();

    String header(String key);

    ByteArray body();

    default String bodyAsString() {
        ByteArray body = body();
        if (body == null) {
            return null;
        }
        return new String(body.toJavaArray());
    }

    default JSON.Instance bodyAsJson() throws JsonParseException {
        ByteArray body = body();
        if (body == null) {
            return null;
        }
        return JSON.parse(new ByteArrayCharStream(body));
    }
}
