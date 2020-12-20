package vproxyx.websocks;

import vproxybase.selector.wrap.file.FileFD;
import vproxybase.util.ByteArray;

public interface PageProvider {
    class PageResult {
        public final String redirect;
        public final String mime;
        public final ByteArray content;
        public final FileFD file;
        public final long cacheAge; // seconds

        public PageResult(String mime, ByteArray content, long cacheAge) {
            this.redirect = null;
            this.mime = mime;
            this.content = content;
            this.file = null;
            this.cacheAge = cacheAge;
        }

        public PageResult(String mime, FileFD file, long cacheAge) {
            this.redirect = null;
            this.mime = mime;
            this.content = null;
            this.file = file;
            this.cacheAge = cacheAge;
        }

        public PageResult(String redirect) {
            this.redirect = redirect;
            this.mime = null;
            this.content = null;
            this.file = null;
            this.cacheAge = 0L;
        }
    }

    PageResult getPage(String url);
}
