package vjson.parser;

public class DefaultParserCacheHolder implements ParserCacheHolder {
    private boolean started = false;

    private static final ThreadLocal<ArrayParser> threadLocalArrayParser = new ThreadLocal<>();
    private static final ThreadLocal<ObjectParser> threadLocalObjectParser = new ThreadLocal<>();
    private static final ThreadLocal<StringParser> threadLocalStringParser = new ThreadLocal<>();

    private static final ThreadLocal<ArrayParser> threadLocalArrayParserJavaObject = new ThreadLocal<>();
    private static final ThreadLocal<ObjectParser> threadLocalObjectParserJavaObject = new ThreadLocal<>();
    private static final ThreadLocal<StringParser> threadLocalStringParserJavaObject = new ThreadLocal<>();

    public boolean isStarted() {
        return started;
    }

    @Override
    public ArrayParser threadLocalArrayParser() {
        return threadLocalArrayParser.get();
    }

    @Override
    public void threadLocalArrayParser(ArrayParser parser) {
        started = true;
        threadLocalArrayParser.set(parser);
    }

    @Override
    public ObjectParser threadLocalObjectParser() {
        return threadLocalObjectParser.get();
    }

    @Override
    public void threadLocalObjectParser(ObjectParser parser) {
        started = true;
        threadLocalObjectParser.set(parser);
    }

    @Override
    public StringParser threadLocalStringParser() {
        return threadLocalStringParser.get();
    }

    @Override
    public void threadLocalStringParser(StringParser parser) {
        started = true;
        threadLocalStringParser.set(parser);
    }

    @Override
    public ArrayParser threadLocalArrayParserJavaObject() {
        return threadLocalArrayParserJavaObject.get();
    }

    @Override
    public void threadLocalArrayParserJavaObject(ArrayParser parser) {
        started = true;
        threadLocalArrayParserJavaObject.set(parser);
    }

    @Override
    public ObjectParser threadLocalObjectParserJavaObject() {
        return threadLocalObjectParserJavaObject.get();
    }

    @Override
    public void threadLocalObjectParserJavaObject(ObjectParser parser) {
        started = true;
        threadLocalObjectParserJavaObject.set(parser);
    }

    @Override
    public StringParser threadLocalStringParserJavaObject() {
        return threadLocalStringParserJavaObject.get();
    }

    @Override
    public void threadLocalStringParserJavaObject(StringParser parser) {
        started = true;
        threadLocalStringParserJavaObject.set(parser);
    }
}
