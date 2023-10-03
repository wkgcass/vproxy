module io.vproxy.dep {
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    requires vjson;

    exports io.vproxy.dep.com.twitter.hpack.hpack;
    exports io.vproxy.dep.tlschannel.impl.impl;
}
