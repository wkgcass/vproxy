package vproxyx.websocks;

import vproxybase.selector.wrap.file.FileFD;
import vproxybase.util.ByteArray;

public interface PageProvider {
    class PageResult {
        public final String redirect;
        public final String mime;
        public final ByteArray content;
        public final FileFD file;

        public PageResult(String mime, ByteArray content) {
            this.redirect = null;
            this.mime = mime;
            this.content = content;
            this.file = null;
        }

        public PageResult(String mime, FileFD file) {
            this.redirect = null;
            this.mime = mime;
            this.content = null;
            this.file = file;
        }

        public PageResult(String redirect) {
            this.redirect = redirect;
            this.mime = null;
            this.content = null;
            this.file = null;
        }
    }

    PageResult getPage(String url);
}
