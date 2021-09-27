module io.vproxy.dep {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;

    exports io.vproxy.dep.com.twitter.hpack.hpack;
    exports io.vproxy.dep.tlschannel.impl.impl;
    exports io.vproxy.dep.vjson;
    exports io.vproxy.dep.vjson.cs;
    exports io.vproxy.dep.vjson.deserializer;
    exports io.vproxy.dep.vjson.deserializer.rule;
    exports io.vproxy.dep.vjson.ex;
    exports io.vproxy.dep.vjson.listener;
    exports io.vproxy.dep.vjson.parser;
    exports io.vproxy.dep.vjson.simple;
    exports io.vproxy.dep.vjson.stringifier;
    exports io.vproxy.dep.vjson.util;
    exports io.vproxy.dep.vjson.util.collection;
    exports io.vproxy.dep.vjson.util.functional;
}
