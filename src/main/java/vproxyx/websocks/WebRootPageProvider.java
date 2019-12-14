package vproxyx.websocks;

import vproxy.util.ByteArray;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public class WebRootPageProvider implements PageProvider {
    private final String baseDir;
    private final ConcurrentHashMap<String, Page> pages = new ConcurrentHashMap<>();

    private static class Page {
        final ByteArray content;
        final long updateTime;

        private Page(ByteArray content, long updateTime) {
            this.content = content;
            this.updateTime = updateTime;
        }
    }

    public WebRootPageProvider(String baseDir) {
        this.baseDir = baseDir;
    }

    private File findFile(String url) {
        // try the url
        Path p = Path.of(baseDir, url);
        File f = p.toFile();
        if (f.exists() && !f.isDirectory()) {
            return f;
        }
        // try to add index.html
        p = Path.of(baseDir, url, "index.html");
        f = p.toFile();
        if (f.exists() && !f.isDirectory()) {
            return f;
        }
        // for url not ending with '/'
        // append '.html'
        if (!url.endsWith("/")) {
            p = Path.of(baseDir, url + ".html");
            f = p.toFile();
            if (f.exists() && !f.isDirectory()) {
                return f;
            }
        }
        // not found
        return null;
    }

    @Override
    public ByteArray getPage(String url) {
        File file = findFile(url);
        if (file == null) {
            return null;
        }
        String key = file.getAbsolutePath();
        if (!file.exists() || file.isDirectory()) {
            pages.remove(key);
            return null;
        }

        long time = file.lastModified();
        Page page = pages.get(key);
        if (page != null) {
            if (time != page.updateTime) {
                pages.remove(key);
                page = null;
            }
        }
        if (page != null) {
            assert Logger.lowLevelDebug("using cached page: " + url);
            return page.content;
        }
        assert Logger.lowLevelDebug("reading from disk: " + url);
        byte[] buf = new byte[1024];
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int r;
            while ((r = fis.read(buf)) >= 0) {
                baos.write(buf, 0, r);
            }
            ByteArray content = ByteArray.from(baos.toByteArray());
            page = new Page(content, time);
            pages.put(key, page);
            return content;
        } catch (IOException e) {
            Logger.error(LogType.FILE_ERROR, "reading file " + key + " failed", e);
            return null;
        }
    }
}
