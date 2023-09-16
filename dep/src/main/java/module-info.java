module io.vproxy.dep {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
    requires transitive vjson;

    exports io.vproxy.dep.com.twitter.hpack.hpack;
    exports io.vproxy.dep.tlschannel.impl.impl;
}
