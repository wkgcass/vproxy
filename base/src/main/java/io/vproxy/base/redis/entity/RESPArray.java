package io.vproxy.base.redis.entity;

import io.vproxy.base.redis.RESPParser;

import java.util.LinkedList;
import java.util.List;

public class RESPArray extends RESP {
    public int len;
    public RESPParser parser;
    public final LinkedList<RESP> array = new LinkedList<>();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        for (RESP r : array) {
            if (isFirst)
                isFirst = false;
            else
                sb.append(",");
            sb.append(r);
        }
        return "RESP.Array([" + sb + "])";
    }

    @Override
    public Object getJavaObject() {
        List<Object> list = new LinkedList<>();
        for (RESP resp : array) {
            list.add(resp.getJavaObject());
        }
        return list;
    }
}
