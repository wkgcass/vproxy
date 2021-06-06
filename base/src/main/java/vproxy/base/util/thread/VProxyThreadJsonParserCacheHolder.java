package vproxy.base.util.thread;

import vjson.parser.ArrayParser;
import vjson.parser.ObjectParser;
import vjson.parser.ParserCacheHolder;
import vjson.parser.StringParser;

public class VProxyThreadJsonParserCacheHolder implements ParserCacheHolder {
    @Override
    public ArrayParser threadLocalArrayParser() {
        return VProxyThread.current().threadLocalArrayParser;
    }

    @Override
    public void threadLocalArrayParser(ArrayParser parser) {
        VProxyThread.current().threadLocalArrayParser = parser;
    }

    @Override
    public ObjectParser threadLocalObjectParser() {
        return VProxyThread.current().threadLocalObjectParser;
    }

    @Override
    public void threadLocalObjectParser(ObjectParser parser) {
        VProxyThread.current().threadLocalObjectParser = parser;
    }

    @Override
    public StringParser threadLocalStringParser() {
        return VProxyThread.current().threadLocalStringParser;
    }

    @Override
    public void threadLocalStringParser(StringParser parser) {
        VProxyThread.current().threadLocalStringParser = parser;
    }

    @Override
    public ArrayParser threadLocalArrayParserJavaObject() {
        return VProxyThread.current().threadLocalArrayParserJavaObject;
    }

    @Override
    public void threadLocalArrayParserJavaObject(ArrayParser parser) {
        VProxyThread.current().threadLocalArrayParserJavaObject = parser;
    }

    @Override
    public ObjectParser threadLocalObjectParserJavaObject() {
        return VProxyThread.current().threadLocalObjectParserJavaObject;
    }

    @Override
    public void threadLocalObjectParserJavaObject(ObjectParser parser) {
        VProxyThread.current().threadLocalObjectParserJavaObject = parser;
    }

    @Override
    public StringParser threadLocalStringParserJavaObject() {
        return VProxyThread.current().threadLocalStringParserJavaObject;
    }

    @Override
    public void threadLocalStringParserJavaObject(StringParser parser) {
        VProxyThread.current().threadLocalStringParserJavaObject = parser;
    }
}
