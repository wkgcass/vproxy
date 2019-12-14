package vproxyx.websocks;

import vproxy.util.ByteArray;

public interface PageProvider {
    ByteArray getPage(String url);
}
