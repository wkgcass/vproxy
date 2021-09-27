package io.vproxy.base.processor.httpbin.hpack;

import io.vproxy.base.processor.httpbin.entity.Header;
import io.vproxy.dep.com.twitter.hpack.hpack.HeaderListener;

import java.util.ArrayList;
import java.util.List;

public class SyncHeaderListener implements HeaderListener {
    private final List<Header> headers = new ArrayList<>(64);

    @Override
    public void addHeader(byte[] name, byte[] value, boolean sensitive) {
        headers.add(new Header(name, value, sensitive));
    }

    public List<Header> getHeaders() {
        List<Header> ret = new ArrayList<>(headers.size());
        ret.addAll(headers);
        headers.clear();
        return ret;
    }
}
