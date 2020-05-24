package vproxyx.websocks;

import vproxybase.util.ByteArray;

public interface PageProvider {
    class PageResult {
        public final String redirect;
        public final String mime;
        public final ByteArray content;

        public PageResult(String mime, ByteArray content) {
            this.redirect = null;
            this.mime = mime;
            this.content = content;
        }

        public PageResult(String redirect) {
            this.redirect = redirect;
            this.mime = null;
            this.content = null;
        }
    }

    PageResult getPage(String url);
}
