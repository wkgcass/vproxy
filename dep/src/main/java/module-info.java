module io.vproxy.dep {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;

    exports com.twitter.hpack;
    exports tlschannel.impl;
    exports vjson;
    exports vjson.cs;
    exports vjson.deserializer;
    exports vjson.deserializer.rule;
    exports vjson.ex;
    exports vjson.listener;
    exports vjson.parser;
    exports vjson.simple;
    exports vjson.stringifier;
    exports vjson.util;
    exports vjson.util.collection;
    exports vjson.util.functional;
}
