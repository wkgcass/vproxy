package vjson.parser;

public interface ParserCacheHolder {
    ArrayParser threadLocalArrayParser();

    void threadLocalArrayParser(ArrayParser parser);

    ObjectParser threadLocalObjectParser();

    void threadLocalObjectParser(ObjectParser parser);

    StringParser threadLocalStringParser();

    void threadLocalStringParser(StringParser parser);

    ArrayParser threadLocalArrayParserJavaObject();

    void threadLocalArrayParserJavaObject(ArrayParser parser);

    ObjectParser threadLocalObjectParserJavaObject();

    void threadLocalObjectParserJavaObject(ObjectParser parser);

    StringParser threadLocalStringParserJavaObject();

    void threadLocalStringParserJavaObject(StringParser parser);
}

