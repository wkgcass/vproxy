package vproxy.vclient;

import java.io.IOException;

public interface ResponseHandler {
    void accept(IOException err, HttpResponse response);
}
